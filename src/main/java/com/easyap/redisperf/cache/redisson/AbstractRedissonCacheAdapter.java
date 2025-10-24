package com.easyap.redisperf.cache.redisson;

import com.easyap.redisperf.cache.CacheAdapter;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.codec.SerializationCodec;

import java.util.concurrent.TimeUnit;

abstract class AbstractRedissonCacheAdapter implements CacheAdapter {

    protected static final SerializationCodec CODEC = new SerializationCodec();

    private final RedissonClient client;

    protected AbstractRedissonCacheAdapter(RedissonClient client) {
        this.client = client;
    }

    protected abstract RBucket<Object> bucketFor(String key);

    protected RedissonClient getClient() {
        return client;
    }

    @Override
    public Object get(String key) {
        return bucketFor(key).get();
    }

    @Override
    public void set(String key, Object value, long ttlSeconds) {
        RBucket<Object> bucket = bucketFor(key);
        if (value == null) {
            bucket.delete();
            return;
        }
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("ttlSeconds must be greater than 0");
        }
        bucket.set(value, ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        client.shutdown();
    }
}
