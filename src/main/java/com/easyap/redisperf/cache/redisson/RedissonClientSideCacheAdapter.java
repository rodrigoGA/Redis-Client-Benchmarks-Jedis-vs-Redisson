package com.easyap.redisperf.cache.redisson;

import org.redisson.api.RBucket;
import org.redisson.api.RClientSideCaching;
import org.redisson.api.RedissonClient;
import org.redisson.api.options.ClientSideCachingOptions;

public class RedissonClientSideCacheAdapter extends AbstractRedissonCacheAdapter {

    private final RClientSideCaching clientSideCaching;

    public RedissonClientSideCacheAdapter(RedissonClient client, int cacheSize) {
        super(client);
        ClientSideCachingOptions options = ClientSideCachingOptions.defaults();
        options = options.size(cacheSize);
        this.clientSideCaching = client.getClientSideCaching(options);
    }

    @Override
    protected RBucket<Object> bucketFor(String key) {
        return clientSideCaching.getBucket(key, CODEC);
    }

    @Override
    public void close() {
        try {
            clientSideCaching.destroy();
        } finally {
            super.close();
        }
    }
}
