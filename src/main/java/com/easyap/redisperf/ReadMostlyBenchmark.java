package com.easyap.redisperf;

import com.easyap.redisperf.cache.CacheAdapter;
import com.easyap.redisperf.config.BenchmarkConfig;
import com.easyap.redisperf.metrics.BenchmarkResult;
import com.easyap.redisperf.metrics.LatencyCollector;
import com.easyap.redisperf.model.VersionedPayload;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class ReadMostlyBenchmark {

    private final BenchmarkConfig config;

    public ReadMostlyBenchmark(BenchmarkConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public Outcome run(String scenarioName,
                       ObjectGenerator<? extends Serializable> generator,
                       Supplier<CacheAdapter> cacheSupplier) {

        int threads = config.getThreadCount();
        int writerThreads = Math.max(1, threads / 4);
        int readerThreads = Math.max(1, threads - writerThreads);
        int iterationsPerThread = config.getIterationsPerThread();

        CacheAdapter writerAdapter = cacheSupplier.get();
        CacheAdapter readerAdapter = cacheSupplier.get();

        ExecutorService executor = Executors.newFixedThreadPool(writerThreads + readerThreads);
        LatencyCollector latencyCollector = new LatencyCollector(config.getLatencySampleSize());
        AtomicLong operations = new AtomicLong();
        AtomicLong versionCounter = new AtomicLong();

        String key = "rw:" + generator.name() + ":" + sanitizeScenario(scenarioName);

        try {
            // Seed value to avoid initial cache miss
            writerAdapter.set(key, new VersionedPayload<>(versionCounter.incrementAndGet(), generator.generate()), config.getTtlSeconds());

            CountDownLatch startLatch = new CountDownLatch(1);

            List<Future<Void>> writerFutures = new ArrayList<>(writerThreads);
            for (int i = 0; i < writerThreads; i++) {
                writerFutures.add(executor.submit(buildWriterTask(writerAdapter, key, generator, versionCounter, operations, iterationsPerThread, startLatch)));
            }

            List<Future<Long>> readerFutures = new ArrayList<>(readerThreads);
            for (int i = 0; i < readerThreads; i++) {
                readerFutures.add(executor.submit(buildReaderTask(readerAdapter, key, latencyCollector, operations, iterationsPerThread, startLatch)));
            }

            long startWall = System.nanoTime();
            startLatch.countDown();

            for (Future<Void> future : writerFutures) {
                awaitFuture(future);
            }

            long maxVersionSeen = 0;
            for (Future<Long> future : readerFutures) {
                maxVersionSeen = Math.max(maxVersionSeen, awaitFuture(future));
            }

            // final read to capture the last version
            Object latest = readerAdapter.get(key);
            if (latest instanceof VersionedPayload) {
                VersionedPayload<?> payload = (VersionedPayload<?>) latest;
                maxVersionSeen = Math.max(maxVersionSeen, payload.getVersion());
            }

            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);

            long duration = System.nanoTime() - startWall;
            long finalVersion = versionCounter.get();
            boolean consistent = maxVersionSeen == finalVersion;

            // cleanup
            writerAdapter.set(key, null, config.getTtlSeconds());

            BenchmarkResult result = new BenchmarkResult(
                    scenarioName + " | ReadMostly",
                    generator.name(),
                    operations.get(),
                    duration,
                    latencyCollector.snapshot()
            );

            return new Outcome(result, writerThreads, readerThreads, finalVersion, maxVersionSeen, consistent);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Benchmark interrupted", ie);
        } finally {
            executor.shutdownNow();
            closeQuietly(writerAdapter);
            closeQuietly(readerAdapter);
        }
    }

    private Callable<Void> buildWriterTask(CacheAdapter cacheAdapter,
                                           String key,
                                           ObjectGenerator<? extends Serializable> generator,
                                           AtomicLong versionCounter,
                                           AtomicLong operations,
                                           int iterations,
                                           CountDownLatch startLatch) {
        return () -> {
            startLatch.await();
            for (int i = 0; i < iterations; i++) {
                long version = versionCounter.incrementAndGet();
                cacheAdapter.set(key, new VersionedPayload<>(version, generator.generate()), config.getTtlSeconds());
                operations.incrementAndGet();
            }
            return null;
        };
    }

    private Callable<Long> buildReaderTask(CacheAdapter cacheAdapter,
                                           String key,
                                           LatencyCollector latencyCollector,
                                           AtomicLong operations,
                                           int iterations,
                                           CountDownLatch startLatch) {
        return () -> {
            startLatch.await();
            long maxVersion = 0;
            for (int i = 0; i < iterations; i++) {
                long start = System.nanoTime();
                Object value = cacheAdapter.get(key);
                long elapsed = System.nanoTime() - start;
                latencyCollector.record(elapsed);
                operations.incrementAndGet();
                if (value instanceof VersionedPayload) {
                    VersionedPayload<?> payload = (VersionedPayload<?>) value;
                    maxVersion = Math.max(maxVersion, payload.getVersion());
                }
            }
            return maxVersion;
        };
    }

    private static String sanitizeScenario(String scenario) {
        return scenario.replaceAll("[^A-Za-z0-9_\\-]", "_");
    }

    private static <T> T awaitFuture(Future<T> future) throws InterruptedException {
        try {
            return future.get();
        } catch (ExecutionException e) {
            throw new IllegalStateException("Worker failed", e.getCause());
        }
    }

    private static void closeQuietly(CacheAdapter adapter) {
        try {
            adapter.close();
        } catch (Exception ignored) {
            // ignore
        }
    }

    public static class Outcome {
        private final BenchmarkResult result;
        private final int writerThreads;
        private final int readerThreads;
        private final long finalVersion;
        private final long maxVersionSeen;
        private final boolean consistent;

        public Outcome(BenchmarkResult result,
                       int writerThreads,
                       int readerThreads,
                       long finalVersion,
                       long maxVersionSeen,
                       boolean consistent) {
            this.result = result;
            this.writerThreads = writerThreads;
            this.readerThreads = readerThreads;
            this.finalVersion = finalVersion;
            this.maxVersionSeen = maxVersionSeen;
            this.consistent = consistent;
        }

        public BenchmarkResult result() {
            return result;
        }

        public int writerThreads() {
            return writerThreads;
        }

        public int readerThreads() {
            return readerThreads;
        }

        public long finalVersion() {
            return finalVersion;
        }

        public long maxVersionSeen() {
            return maxVersionSeen;
        }

        public boolean consistent() {
            return consistent;
        }
    }
}
