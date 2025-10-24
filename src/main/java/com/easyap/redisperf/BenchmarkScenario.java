package com.easyap.redisperf;

import com.easyap.redisperf.cache.CacheAdapter;

import java.util.Objects;
import java.util.function.Supplier;

public final class BenchmarkScenario {

    private final String name;
    private final String description;
    private final Supplier<CacheAdapter> cacheSupplier;

    public BenchmarkScenario(String name, String description, Supplier<CacheAdapter> cacheSupplier) {
        this.name = Objects.requireNonNull(name, "name");
        this.description = Objects.requireNonNull(description, "description");
        this.cacheSupplier = Objects.requireNonNull(cacheSupplier, "cacheSupplier");
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public Supplier<CacheAdapter> cacheSupplier() {
        return cacheSupplier;
    }
}
