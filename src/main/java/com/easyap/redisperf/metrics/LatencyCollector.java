package com.easyap.redisperf.metrics;

import java.util.Arrays;

public class LatencyCollector {

    private final long[] buffer;
    private int cursor;
    private boolean filled;

    public LatencyCollector(int sampleSize) {
        if (sampleSize <= 0) {
            throw new IllegalArgumentException("sampleSize must be > 0");
        }
        this.buffer = new long[sampleSize];
    }

    public void record(long nanos) {
        synchronized (buffer) {
            buffer[cursor] = nanos;
            cursor++;
            if (cursor >= buffer.length) {
                cursor = 0;
                filled = true;
            }
        }
    }

    public LatencySnapshot snapshot() {
        long[] copy;
        synchronized (buffer) {
            int size = filled ? buffer.length : cursor;
            copy = Arrays.copyOf(buffer, size);
        }
        if (copy.length == 0) {
            return new LatencySnapshot(0, 0, 0);
        }
        Arrays.sort(copy);
        long p50 = percentile(copy, 0.50);
        long p95 = percentile(copy, 0.95);
        long p99 = percentile(copy, 0.99);
        return new LatencySnapshot(p50, p95, p99);
    }

    private static long percentile(long[] sorted, double percentile) {
        if (sorted.length == 0) {
            return 0;
        }
        int index = (int) Math.ceil(percentile * sorted.length) - 1;
        if (index < 0) {
            index = 0;
        } else if (index >= sorted.length) {
            index = sorted.length - 1;
        }
        return sorted[index];
    }

    public static class LatencySnapshot {

        private final long p50Nanos;
        private final long p95Nanos;
        private final long p99Nanos;

        public LatencySnapshot(long p50Nanos, long p95Nanos, long p99Nanos) {
            this.p50Nanos = p50Nanos;
            this.p95Nanos = p95Nanos;
            this.p99Nanos = p99Nanos;
        }

        public long getP50Nanos() {
            return p50Nanos;
        }

        public long getP95Nanos() {
            return p95Nanos;
        }

        public long getP99Nanos() {
            return p99Nanos;
        }

        public double p50Millis() {
            return nanosToMillis(p50Nanos);
        }

        public double p95Millis() {
            return nanosToMillis(p95Nanos);
        }

        public double p99Millis() {
            return nanosToMillis(p99Nanos);
        }

        private static double nanosToMillis(long nanos) {
            return nanos / 1_000_000.0;
        }
    }
}
