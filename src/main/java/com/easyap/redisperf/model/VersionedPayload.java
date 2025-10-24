package com.easyap.redisperf.model;

import java.io.Serializable;

public class VersionedPayload<T extends Serializable> implements Serializable {

    private static final long serialVersionUID = 8L;

    private final long version;
    private final T payload;

    public VersionedPayload(long version, T payload) {
        this.version = version;
        this.payload = payload;
    }

    public long getVersion() {
        return version;
    }

    public T getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        return "VersionedPayload{" +
                "version=" + version +
                ", payloadClass=" + (payload != null ? payload.getClass().getSimpleName() : "null") +
                '}';
    }
}
