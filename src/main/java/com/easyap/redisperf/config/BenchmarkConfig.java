package com.easyap.redisperf.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public final class BenchmarkConfig {

    private final String redisUri;
    private final int threadCount;
    private final int iterationsPerThread;
    private final long ttlSeconds;
    private final Duration warmupDuration;
    private final int latencySampleSize;
    private final int scenarioRuns;

    private BenchmarkConfig(Builder builder) {
        this.redisUri = builder.redisUri;
        this.threadCount = builder.threadCount;
        this.iterationsPerThread = builder.iterationsPerThread;
        this.ttlSeconds = builder.ttlSeconds;
        this.warmupDuration = builder.warmupDuration;
        this.latencySampleSize = builder.latencySampleSize;
        this.scenarioRuns = builder.scenarioRuns;
    }

    public String getRedisUri() {
        return redisUri;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public int getIterationsPerThread() {
        return iterationsPerThread;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public Duration getWarmupDuration() {
        return warmupDuration;
    }

    public int getLatencySampleSize() {
        return latencySampleSize;
    }

    public int getScenarioRuns() {
        return scenarioRuns;
    }

    public static BenchmarkConfig fromArgs(String[] args) {
        Map<String, String> overrides = parseArgs(args);
        Builder builder = new Builder();

        overrides.forEach(builder::override);

        return builder.build();
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> values = new HashMap<>();
        if (args == null) {
            return values;
        }
        for (String arg : args) {
            if (arg == null || arg.trim().isEmpty()) {
                continue;
            }
            String normalized = arg.trim();
            if (normalized.startsWith("--")) {
                normalized = normalized.substring(2);
            }
            int eq = normalized.indexOf('=');
            if (eq > 0 && eq < normalized.length() - 1) {
                String key = normalized.substring(0, eq).trim();
                String value = normalized.substring(eq + 1).trim();
                if (!key.isEmpty()) {
                    values.put(key, value);
                }
            }
        }
        return values;
    }

    public static final class Builder {
        private String redisUri = System.getenv().getOrDefault("REDIS_URI", "redis://127.0.0.1:6379");
        private int threadCount = getEnvInt("BENCHMARK_THREADS", 8);
        private int iterationsPerThread = getEnvInt("BENCHMARK_ITERATIONS", 10_000);
        private long ttlSeconds = getEnvLong("BENCHMARK_TTL_SECONDS", 600L);
        private Duration warmupDuration = Duration.ofSeconds(getEnvLong("BENCHMARK_WARMUP_SECONDS", 10L));
        private int latencySampleSize = getEnvInt("BENCHMARK_LATENCY_SAMPLE", 5_000);
        private int scenarioRuns = getEnvInt("BENCHMARK_RUNS", 1);

        private static int getEnvInt(String name, int defaultValue) {
            String value = System.getenv(name);
            if (value == null || value.trim().isEmpty()) {
                return defaultValue;
            }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException nfe) {
                return defaultValue;
            }
        }

        private static long getEnvLong(String name, long defaultValue) {
            String value = System.getenv(name);
            if (value == null || value.trim().isEmpty()) {
                return defaultValue;
            }
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException nfe) {
                return defaultValue;
            }
        }

        private void override(String property, String value) {
            if (property == null || value == null) {
                return;
            }
            switch (property) {
                case "redisUri":
                    redisUri = value;
                    break;
                case "threads":
                    threadCount = parseInt(value, threadCount);
                    break;
                case "iterations":
                    iterationsPerThread = parseInt(value, iterationsPerThread);
                    break;
                case "ttlSeconds":
                    ttlSeconds = parseLong(value, ttlSeconds);
                    break;
                case "warmupSeconds":
                    warmupDuration = Duration.ofSeconds(parseLong(value, warmupDuration.getSeconds()));
                    break;
                case "latencySample":
                    latencySampleSize = parseInt(value, latencySampleSize);
                    break;
                case "runs":
                    scenarioRuns = parseInt(value, scenarioRuns);
                    break;
                default:
                    break;
            }
        }

        private static int parseInt(String value, int defaultValue) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException nfe) {
                return defaultValue;
            }
        }

        private static long parseLong(String value, long defaultValue) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException nfe) {
                return defaultValue;
            }
        }

        public BenchmarkConfig build() {
            if (threadCount <= 0) {
                throw new IllegalArgumentException("threadCount must be > 0");
            }
            if (iterationsPerThread <= 0) {
                throw new IllegalArgumentException("iterationsPerThread must be > 0");
            }
            if (ttlSeconds <= 0) {
                throw new IllegalArgumentException("ttlSeconds must be > 0");
            }
            if (latencySampleSize <= 0) {
                throw new IllegalArgumentException("latencySampleSize must be > 0");
            }
            if (scenarioRuns <= 0) {
                throw new IllegalArgumentException("scenarioRuns must be > 0");
            }
            return new BenchmarkConfig(this);
        }
    }
}
