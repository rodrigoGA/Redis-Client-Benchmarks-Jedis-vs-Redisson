package com.easyap.redisperf;

import com.easyap.redisperf.cache.CacheAdapter;
import com.easyap.redisperf.cache.jedis.JedisCacheAdapter;
import com.easyap.redisperf.cache.redisson.RedissonCacheAdapter;
import com.easyap.redisperf.cache.redisson.RedissonClientSideCacheAdapter;
import com.easyap.redisperf.config.BenchmarkConfig;
import com.easyap.redisperf.metrics.BenchmarkResult;
import com.easyap.redisperf.model.CustomerProfile;
import com.easyap.redisperf.model.InventorySnapshot;
import com.easyap.redisperf.model.LargeTextDocument;
import com.easyap.redisperf.model.MetricsBatch;
import com.easyap.redisperf.model.OrderAggregate;
import com.easyap.redisperf.model.PlainTextMessage;
import org.apache.commons.lang3.RandomStringUtils;
import org.redisson.api.RBucket;
import org.redisson.api.RClientSideCaching;
import org.redisson.api.RedissonClient;
import org.redisson.api.options.ClientSideCachingOptions;
import org.redisson.codec.SerializationCodec;
import redis.clients.jedis.JedisPooled;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class BenchmarkApplication {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
    private static final int LOCAL_CACHE_MAX_SIZE = 20_000;

    public static void main(String[] args) {
        BenchmarkConfig config = BenchmarkConfig.fromArgs(args);
        RedisClientFactory clientFactory = new RedisClientFactory(config.getRedisUri());
        BenchmarkRunner runner = new BenchmarkRunner(config);
        ReadMostlyBenchmark readMostlyBenchmark = new ReadMostlyBenchmark(config);

        List<ObjectGenerator<?>> generators = Arrays.asList(
                new ObjectGenerator<>("CustomerProfile", CustomerProfile::random),
                new ObjectGenerator<>("OrderAggregate", OrderAggregate::random),
                new ObjectGenerator<>("InventorySnapshot", InventorySnapshot::random),
                new ObjectGenerator<>("PlainTextMessage", PlainTextMessage::random),
                new ObjectGenerator<>("LargeTextDocument", LargeTextDocument::random),
                new ObjectGenerator<>("MetricsBatch", MetricsBatch::random),
                new ObjectGenerator<>("LargeString", () -> RandomStringUtils.randomAlphabetic(2_048))
        );

        List<BenchmarkScenario> scenarios = Arrays.asList(
                new BenchmarkScenario(
                        "Jedis (no cache)",
                        "UnifiedJedis with manual Java serialization and no local cache.",
                        () -> new JedisCacheAdapter(clientFactory.createJedis())
                ),
                new BenchmarkScenario(
                        "Jedis client cache",
                        "UnifiedJedis using redis.clients.jedis.csc with a 20k-entry local cache.",
                        () -> new JedisCacheAdapter(clientFactory.createCachedJedis(LOCAL_CACHE_MAX_SIZE))
                ),
                new BenchmarkScenario(
                        "Redisson SerializationCodec",
                        "Redisson RBucket with SerializationCodec (RESP3) and no local cache.",
                        () -> new RedissonCacheAdapter(clientFactory.createRedisson())
                ),
                new BenchmarkScenario(
                        "Redisson client cache",
                        "Redisson RClientSideCaching (native RESP3 tracking) with a 20k-entry local cache.",
                        () -> new RedissonClientSideCacheAdapter(clientFactory.createRedisson(), LOCAL_CACHE_MAX_SIZE)
                )
        );

        List<BenchmarkRecord> records = new ArrayList<>();
        List<String> setGetDiagnostics = new ArrayList<>();
        List<String> readMostlyDiagnostics = new ArrayList<>();
        List<String> anomalyDiagnostics = new ArrayList<>();
        Map<String, ReadMostlyBenchmark.Outcome> readMostlyOutcomeMap = new HashMap<>();

        System.out.printf(
                Locale.ROOT,
                "== Redis Benchmark ==%nRedis URI: %s%nThreads: %d | Iterations per thread: %d | TTL: %d s%n%n",
                config.getRedisUri(),
                config.getThreadCount(),
                config.getIterationsPerThread(),
                config.getTtlSeconds()
        );

        for (ObjectGenerator<?> generator : generators) {
            for (BenchmarkScenario scenario : scenarios) {
                flushDatabase(clientFactory);
                printTestHeader(TestMode.SET_GET, scenario, generator.name());
                try (CacheAdapter cacheAdapter = scenario.cacheSupplier().get()) {
                    BenchmarkResult result = runner.runScenario(scenario.name() + " | SetGet", generator, cacheAdapter);
                    records.add(new BenchmarkRecord(TestMode.SET_GET, scenario.name(), generator.name(), result));
                    setGetDiagnostics.add(formatSetGetDiagnostic(scenario.name(), generator.name(), result));
                    printResult(result);
                }

                flushDatabase(clientFactory);
                printTestHeader(TestMode.READ_MOSTLY, scenario, generator.name());
                ReadMostlyBenchmark.Outcome outcome = readMostlyBenchmark.run(scenario.name(), generator, scenario.cacheSupplier());
                readMostlyOutcomeMap.put(scenario.name() + "|" + generator.name(), outcome);
                BenchmarkResult readMostlyResult = outcome.result();
                records.add(new BenchmarkRecord(TestMode.READ_MOSTLY, scenario.name(), generator.name(), readMostlyResult));
                printResult(readMostlyResult);
                String readDiag = formatReadMostlyDiagnostic(scenario.name(), generator.name(), outcome);
                readMostlyDiagnostics.add(readDiag);
                System.out.println("  " + readDiag);
                if (!outcome.consistent()) {
                anomalyDiagnostics.add("Inconsistency detected: " + readDiag);
                }

                flushDatabase(clientFactory);
            }
        }

        boolean invalidationOk = runClientSideCachingInvalidationProbe(clientFactory);

        System.out.println();
        List<String> summaryTable = buildSummaryTable(records);
        summaryTable.forEach(System.out::println);

        List<String> winners = computeWinners(records);
        System.out.println();
        System.out.println("=== Winners per workload ===");
        winners.forEach(line -> System.out.println(" - " + line));

        persistResults(records, readMostlyOutcomeMap, setGetDiagnostics, readMostlyDiagnostics, anomalyDiagnostics, winners, invalidationOk);

        System.out.println();
        System.out.println("Consolidated results saved to benchmark-results/latest.csv");
        System.out.printf("Client-side caching invalidation probe: %s%n", invalidationOk ? "OK" : "FAILED");
    }

    private static void printTestHeader(TestMode mode, BenchmarkScenario scenario, String objectName) {
        System.out.println();
        System.out.printf("=== %s | Object: %s | Scenario: %s ===%n", mode.title(), objectName, scenario.name());
        System.out.println("  " + scenario.description());
        System.out.println("  " + mode.description());
    }

    private static void printResult(BenchmarkResult result) {
        System.out.printf(
                Locale.ROOT,
                "  Ops: %,d | Time: %.2f s | Throughput: %.0f ops/s | Average latency: %.2f ms | p50/p95/p99: %.2f / %.2f / %.2f ms%n",
                result.getOperations(),
                result.getDurationNanos() / 1_000_000_000.0,
                result.throughputPerSecond(),
                result.averageLatencyMillis(),
                result.getLatencySnapshot().p50Millis(),
                result.getLatencySnapshot().p95Millis(),
                result.getLatencySnapshot().p99Millis()
        );
    }

    private static String formatSetGetDiagnostic(String scenarioName, String objectName, BenchmarkResult result) {
        return String.format(
                Locale.ROOT,
                "Set/Get | Scenario=%s | Object=%s | Throughput=%.0f ops/s | Avg=%.2f ms | p95=%.2f ms | p99=%.2f ms",
                scenarioName,
                objectName,
                result.throughputPerSecond(),
                result.averageLatencyMillis(),
                result.getLatencySnapshot().p95Millis(),
                result.getLatencySnapshot().p99Millis()
        );
    }

    private static String formatReadMostlyDiagnostic(String scenarioName,
                                                     String objectName,
                                                     ReadMostlyBenchmark.Outcome outcome) {
        BenchmarkResult result = outcome.result();
        return String.format(
                Locale.ROOT,
                "ReadMostly | Scenario=%s | Object=%s | Writers=%d | Readers=%d | Throughput=%.0f ops/s | FinalVersion=%d | Observed=%d | Consistency=%s",
                scenarioName,
                objectName,
                outcome.writerThreads(),
                outcome.readerThreads(),
                result.throughputPerSecond(),
                outcome.finalVersion(),
                outcome.maxVersionSeen(),
                outcome.consistent() ? "OK" : "FAILED"
        );
    }

    private static List<String> buildSummaryTable(List<BenchmarkRecord> records) {
        List<String> lines = new ArrayList<>();
        lines.add("=== Comparative Table ===");
        String header = String.format(Locale.ROOT,
                "%-18s | %-26s | %-22s | %12s | %9s | %9s | %9s",
                "Mode",
                "Scenario",
                "Object",
                "Ops/s",
                "Avg(ms)",
                "p95(ms)",
                "p99(ms)");
        lines.add(header);
        lines.add(repeat('-', header.length()));

        records.stream()
                .sorted(Comparator
                        .comparing((BenchmarkRecord r) -> r.mode().ordinal())
                        .thenComparing(BenchmarkRecord::objectName)
                        .thenComparing(BenchmarkRecord::scenarioName))
                .forEach(record -> {
                    BenchmarkResult result = record.result();
                    lines.add(String.format(
                            Locale.ROOT,
                            "%-18s | %-26s | %-22s | %12.0f | %9.2f | %9.2f | %9.2f",
                            record.mode().title(),
                            record.scenarioName(),
                            record.objectName(),
                            result.throughputPerSecond(),
                            result.averageLatencyMillis(),
                            result.getLatencySnapshot().p95Millis(),
                            result.getLatencySnapshot().p99Millis()
                    ));
                });

        return lines;
    }

    private static List<String> computeWinners(List<BenchmarkRecord> records) {
        Map<ModeObjectKey, BenchmarkRecord> bestByMode = new HashMap<>();
        for (BenchmarkRecord record : records) {
            ModeObjectKey key = new ModeObjectKey(record.mode(), record.objectName());
            bestByMode.merge(key, record, (current, candidate) ->
                    candidate.result().throughputPerSecond() > current.result().throughputPerSecond() ? candidate : current);
        }

        return bestByMode.entrySet().stream()
                .sorted(Map.Entry.<ModeObjectKey, BenchmarkRecord>comparingByKey(
                        new Comparator<ModeObjectKey>() {
                            @Override
                            public int compare(ModeObjectKey o1, ModeObjectKey o2) {
                                int modeCompare = Integer.compare(o1.mode().ordinal(), o2.mode().ordinal());
                                if (modeCompare != 0) {
                                    return modeCompare;
                                }
                                return o1.objectName().compareTo(o2.objectName());
                            }
                        }))
                .map(entry -> {
                    BenchmarkRecord record = entry.getValue();
                    BenchmarkResult result = record.result();
                    return String.format(
                            Locale.ROOT,
                            "%s - %s: %s (%.0f ops/s, p95=%.2f ms)",
                            entry.getKey().mode().title(),
                            entry.getKey().objectName(),
                            record.scenarioName(),
                            result.throughputPerSecond(),
                            result.getLatencySnapshot().p95Millis()
                    );
                })
                .collect(Collectors.toList());
    }

    private static boolean runClientSideCachingInvalidationProbe(RedisClientFactory factory) {
        System.out.println();
        System.out.println("== Redisson Client-Side Cache invalidation probe ==");
        String cacheKey = "perf:invalidation:probe";
        String firstValue = "initial-" + System.nanoTime();
        String secondValue = "updated-" + System.nanoTime();
        RedissonClient cachedClient = factory.createRedisson();
        RedissonClient writerClient = factory.createRedisson();
        try {
            RClientSideCaching caching = cachedClient.getClientSideCaching(ClientSideCachingOptions.defaults());
            RBucket<String> cachedBucket = caching.getBucket(cacheKey, new SerializationCodec());
            RBucket<String> writerBucket = writerClient.getBucket(cacheKey, new SerializationCodec());

            cachedBucket.set(firstValue);
            String cacheHit = cachedBucket.get(); // populate local cache

            writerBucket.set(secondValue);
            Thread.sleep(100); // give the invalidation some time

            String latest = cachedBucket.get();
            caching.destroy();

            boolean ok = secondValue.equals(latest);
            System.out.printf("Initial read: %s | After external update: %s -> %s%n",
                    cacheHit, latest, ok ? "OK" : "FAILED");
            writerBucket.delete();
            return ok;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            System.err.println("Client-side invalidation probe failed: " + e.getMessage());
            return false;
        } finally {
            cachedClient.shutdown();
            writerClient.shutdown();
        }
    }

    private static void persistResults(List<BenchmarkRecord> records,
                                       Map<String, ReadMostlyBenchmark.Outcome> readMostlyOutcomeMap,
                                       List<String> setGetDiagnostics,
                                       List<String> readMostlyDiagnostics,
                                       List<String> anomalyDiagnostics,
                                       List<String> winners,
                                       boolean invalidationOk) {
        Path directory = Paths.get("benchmark-results");
        try {
            Files.createDirectories(directory);

            List<String> csvLines = new ArrayList<>();
            csvLines.add("mode,scenario,object,operations,seconds,ops_per_sec,average_ms,p50_ms,p95_ms,p99_ms,writer_threads,reader_threads,final_version,max_observed_version,consistent");
            for (BenchmarkRecord record : records) {
                BenchmarkResult result = record.result();
                double seconds = result.getDurationNanos() / 1_000_000_000.0;
                String writerThreads = "";
                String readerThreads = "";
                String finalVersion = "";
                String observedVersion = "";
                String consistent = "";
                if (record.mode() == TestMode.READ_MOSTLY) {
                    ReadMostlyBenchmark.Outcome outcome = readMostlyOutcomeMap.get(record.scenarioName() + "|" + record.objectName());
                    if (outcome != null) {
                        writerThreads = String.valueOf(outcome.writerThreads());
                        readerThreads = String.valueOf(outcome.readerThreads());
                        finalVersion = String.valueOf(outcome.finalVersion());
                        observedVersion = String.valueOf(outcome.maxVersionSeen());
                        consistent = outcome.consistent() ? "true" : "false";
                    }
                }
                csvLines.add(String.format(Locale.ROOT,
                        "%s,%s,%s,%d,%.6f,%.0f,%.2f,%.2f,%.2f,%.2f,%s,%s,%s,%s,%s",
                        record.mode().title(),
                        escapeCsv(record.scenarioName()),
                        escapeCsv(record.objectName()),
                        result.getOperations(),
                        seconds,
                        result.throughputPerSecond(),
                        result.averageLatencyMillis(),
                        result.getLatencySnapshot().p50Millis(),
                        result.getLatencySnapshot().p95Millis(),
                        result.getLatencySnapshot().p99Millis(),
                        writerThreads,
                        readerThreads,
                        finalVersion,
                        observedVersion,
                        consistent));
            }

            Path output = directory.resolve("latest.csv");
            Files.write(output, csvLines);

            String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT).format(LocalDateTime.now());
            Path archived = directory.resolve("run-" + timestamp + ".csv");
            Files.write(archived, csvLines);

            List<String> notes = new ArrayList<>();
            notes.add("Redis Benchmark Report");
            notes.add("Generated: " + FORMATTER.format(LocalDateTime.now()));
            notes.add("");
            notes.add("Winners per workload:");
            if (winners.isEmpty()) {
                notes.add("  (none)");
            } else {
                winners.forEach(win -> notes.add("  " + win));
            }
            notes.add("");
            notes.add("Set/Get diagnostics:");
            if (setGetDiagnostics.isEmpty()) {
                notes.add("  (none)");
            } else {
                setGetDiagnostics.forEach(diag -> notes.add("  " + diag));
            }
            notes.add("");
            notes.add("Read-Mostly diagnostics:");
            if (readMostlyDiagnostics.isEmpty()) {
                notes.add("  (none)");
            } else {
                readMostlyDiagnostics.forEach(diag -> notes.add("  " + diag));
            }
            notes.add("");
            notes.add("Inconsistencies:");
            if (anomalyDiagnostics.isEmpty()) {
                notes.add("  (none)");
            } else {
                anomalyDiagnostics.forEach(a -> notes.add("  " + a));
            }
            notes.add("");
            notes.add("Client-side cache invalidation probe: " + (invalidationOk ? "OK" : "FAILED"));
            Files.write(directory.resolve("latest-notes.txt"), notes);
        } catch (IOException e) {
            System.err.println("Failed to write benchmark artefacts: " + e.getMessage());
        }
    }

    private static void flushDatabase(RedisClientFactory factory) {
        try (JedisPooled jedis = factory.createJedis()) {
            jedis.flushAll();
        }
    }

    private static String repeat(char value, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }

    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static class ModeObjectKey {
        private final TestMode mode;
        private final String objectName;

        ModeObjectKey(TestMode mode, String objectName) {
            this.mode = Objects.requireNonNull(mode, "mode");
            this.objectName = Objects.requireNonNull(objectName, "objectName");
        }

        TestMode mode() {
            return mode;
        }

        String objectName() {
            return objectName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ModeObjectKey that = (ModeObjectKey) o;
            return mode == that.mode && objectName.equals(that.objectName);
        }

        @Override
        public int hashCode() {
            return mode.hashCode() * 31 + objectName.hashCode();
        }
    }
}
