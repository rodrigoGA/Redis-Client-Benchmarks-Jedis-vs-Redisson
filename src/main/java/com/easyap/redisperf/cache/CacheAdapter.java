package com.easyap.redisperf.cache;

public interface CacheAdapter extends AutoCloseable {

    Object get(String key);

    void set(String key, Object value, long ttlSeconds);

    @Override
    void close();
}
