package com.easyap.redisperf.cache.redisson;

import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

public class RedissonCacheAdapter extends AbstractRedissonCacheAdapter {

    public RedissonCacheAdapter(RedissonClient client) {
        super(client);
    }

    @Override
    protected RBucket<Object> bucketFor(String key) {
        return getClient().getBucket(key, CODEC);
    }
}
