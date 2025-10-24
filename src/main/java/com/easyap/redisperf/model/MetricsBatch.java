package com.easyap.redisperf.model;

import org.apache.commons.lang3.RandomStringUtils;

import java.io.Serializable;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

public class MetricsBatch implements Serializable {

    private static final long serialVersionUID = 6L;

    private final String sourceSystem;
    private final Instant generatedAt;
    private final double[] metrics;

    public MetricsBatch(String sourceSystem, Instant generatedAt, double[] metrics) {
        this.sourceSystem = sourceSystem;
        this.generatedAt = generatedAt;
        this.metrics = metrics;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public double[] getMetrics() {
        return metrics;
    }

    public static MetricsBatch random() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        String source = "SYS-" + RandomStringUtils.randomAlphanumeric(6).toUpperCase();
        Instant timestamp = Instant.now().minusSeconds(rnd.nextLong(0, 3_600));
        double[] metrics = new double[256];
        for (int i = 0; i < metrics.length; i++) {
            metrics[i] = rnd.nextDouble(-1_000.0, 1_000.0);
        }
        return new MetricsBatch(source, timestamp, metrics);
    }
}
