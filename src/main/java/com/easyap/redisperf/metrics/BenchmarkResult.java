package com.easyap.redisperf.metrics;

public class BenchmarkResult {

    private final String scenario;
    private final String objectType;
    private final long operations;
    private final long durationNanos;
    private final LatencyCollector.LatencySnapshot latencySnapshot;

    public BenchmarkResult(String scenario,
                           String objectType,
                           long operations,
                           long durationNanos,
                           LatencyCollector.LatencySnapshot latencySnapshot) {
        this.scenario = scenario;
        this.objectType = objectType;
        this.operations = operations;
        this.durationNanos = durationNanos;
        this.latencySnapshot = latencySnapshot;
    }

    public String getScenario() {
        return scenario;
    }

    public String getObjectType() {
        return objectType;
    }

    public long getOperations() {
        return operations;
    }

    public long getDurationNanos() {
        return durationNanos;
    }

    public LatencyCollector.LatencySnapshot getLatencySnapshot() {
        return latencySnapshot;
    }

    public double throughputPerSecond() {
        double seconds = durationNanos / 1_000_000_000.0;
        if (seconds == 0) {
            return 0.0;
        }
        return operations / seconds;
    }

    public double averageLatencyMillis() {
        if (operations == 0) {
            return 0.0;
        }
        double avgNanos = (double) durationNanos / operations;
        return avgNanos / 1_000_000.0;
    }
}
