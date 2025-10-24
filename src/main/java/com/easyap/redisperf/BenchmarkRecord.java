package com.easyap.redisperf;

import com.easyap.redisperf.metrics.BenchmarkResult;

import java.util.Objects;

public final class BenchmarkRecord {

    private final TestMode mode;
    private final String scenarioName;
    private final String objectName;
    private final BenchmarkResult result;

    public BenchmarkRecord(TestMode mode, String scenarioName, String objectName, BenchmarkResult result) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.scenarioName = Objects.requireNonNull(scenarioName, "scenarioName");
        this.objectName = Objects.requireNonNull(objectName, "objectName");
        this.result = Objects.requireNonNull(result, "result");
    }

    public TestMode mode() {
        return mode;
    }

    public String scenarioName() {
        return scenarioName;
    }

    public String objectName() {
        return objectName;
    }

    public BenchmarkResult result() {
        return result;
    }
}
