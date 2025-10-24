package com.easyap.redisperf;

import java.io.Serializable;
import java.util.Objects;
import java.util.function.Supplier;

public final class ObjectGenerator<T extends Serializable> {

    private final String name;
    private final Supplier<T> supplier;

    public ObjectGenerator(String name, Supplier<T> supplier) {
        this.name = Objects.requireNonNull(name, "name");
        this.supplier = Objects.requireNonNull(supplier, "supplier");
    }

    public String name() {
        return name;
    }

    public T generate() {
        return supplier.get();
    }
}
