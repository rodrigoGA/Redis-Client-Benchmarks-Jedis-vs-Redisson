package com.easyap.redisperf;

import com.easyap.redisperf.cache.CacheAdapter;
import com.easyap.redisperf.config.BenchmarkConfig;
import com.easyap.redisperf.metrics.BenchmarkResult;
import com.easyap.redisperf.metrics.LatencyCollector;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class BenchmarkRunner {

    private final BenchmarkConfig config;

    public BenchmarkRunner(BenchmarkConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public BenchmarkResult runScenario(String scenario,
                                       ObjectGenerator<?> generator,
                                       CacheAdapter cacheAdapter) {

        int threads = config.getThreadCount();
        int iterationsPerThread = config.getIterationsPerThread();
        int totalIterations = threads * iterationsPerThread;

        List<BenchmarkValue> values = generatePayloads(generator, scenario, iterationsPerThread, threads);

        performWarmup(cacheAdapter, values);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        LatencyCollector latencyCollector = new LatencyCollector(config.getLatencySampleSize());
        AtomicLong operations = new AtomicLong();

        long startWall = System.nanoTime();

        List<Future<Void>> futures = new ArrayList<>(threads);
        for (int t = 0; t < threads; t++) {
            int index = t;
            futures.add(executor.submit(buildWorker(cacheAdapter, values, latencyCollector, operations, iterationsPerThread, index)));
        }

        for (Future<Void> f : futures) {
            try {
                f.get();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Benchmark interrupted", ie);
            } catch (ExecutionException e) {
                throw new IllegalStateException("Benchmark worker failed", e.getCause());
            }
        }

        executor.shutdown();
        try {
            executor.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long duration = System.nanoTime() - startWall;

        LatencyCollector.LatencySnapshot snapshot = latencyCollector.snapshot();

        // cleanup keys quickly (best-effort)
        for (BenchmarkValue value : values) {
            try {
                cacheAdapter.set(value.key(), null, config.getTtlSeconds());
            } catch (Exception ignored) {
                // Best-effort cleanup
            }
        }

        return new BenchmarkResult(scenario, generator.name(), operations.get(), duration, snapshot);
    }

    private Callable<Void> buildWorker(CacheAdapter cacheAdapter,
                                       List<BenchmarkValue> values,
                                       LatencyCollector latencyCollector,
                                       AtomicLong operations,
                                       int iterationsPerThread,
                                       int threadIndex) {
        return () -> {
            int startIndex = threadIndex * iterationsPerThread;
            int endIndex = startIndex + iterationsPerThread;
            for (int i = startIndex; i < endIndex; i++) {
                BenchmarkValue value = values.get(i);
                long iterationStart = System.nanoTime();
                cacheAdapter.set(value.key(), value.payload(), config.getTtlSeconds());
                Object retrieved = cacheAdapter.get(value.key());
                long elapsed = System.nanoTime() - iterationStart;
                latencyCollector.record(elapsed);
                operations.addAndGet(2);
                if (retrieved == null) {
                    throw new IllegalStateException("Unexpected null value for key " + value.key());
                }
            }
            return null;
        };
    }

    private void performWarmup(CacheAdapter cacheAdapter, List<BenchmarkValue> values) {
        long warmupDeadline = System.nanoTime() + config.getWarmupDuration().toNanos();
        int index = 0;
        while (System.nanoTime() < warmupDeadline) {
            BenchmarkValue value = values.get(index % values.size());
            cacheAdapter.set(value.key(), value.payload(), config.getTtlSeconds());
            cacheAdapter.get(value.key());
            index++;
        }
    }

    private List<BenchmarkValue> generatePayloads(ObjectGenerator<?> generator,
                                                  String scenario,
                                                  int iterationsPerThread,
                                                  int threads) {
        int total = iterationsPerThread * threads;
        return IntStream.range(0, total)
                .mapToObj(i -> new BenchmarkValue(buildKey(generator.name(), scenario, i), generator.generate()))
                .collect(Collectors.toList());
    }

    private String buildKey(String generatorName, String scenario, int index) {
        String sanitizedScenario = scenario.replaceAll("[^A-Za-z0-9_\\-]", "_");
        return "perf:" + generatorName + ":" + sanitizedScenario + ":" + index;
    }

    private static class BenchmarkValue {
        private final String key;
        private final Object payload;

        BenchmarkValue(String key, Object payload) {
            this.key = key;
            this.payload = payload;
        }

        String key() {
            return key;
        }

        Object payload() {
            return payload;
        }
    }
}
