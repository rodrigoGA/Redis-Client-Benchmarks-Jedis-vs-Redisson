package com.easyap.redisperf.cache.jedis;

import com.easyap.redisperf.cache.CacheAdapter;
import com.easyap.redisperf.util.JavaSerializationUtils;
import redis.clients.jedis.JedisPooled;

import java.nio.charset.StandardCharsets;

public class JedisCacheAdapter implements CacheAdapter {

    private final JedisPooled jedis;

    public JedisCacheAdapter(JedisPooled jedis) {
        this.jedis = jedis;
    }

    @Override
    public Object get(String key) {
        byte[] data = jedis.get(key.getBytes(StandardCharsets.UTF_8));
        return JavaSerializationUtils.deserialize(data);
    }

    @Override
    public void set(String key, Object value, long ttlSeconds) {
        if (value == null) {
            jedis.del(key);
            return;
        }
        byte[] payload = JavaSerializationUtils.serialize(value);
        jedis.setex(key.getBytes(StandardCharsets.UTF_8), (int) ttlSeconds, payload);
    }

    @Override
    public void close() {
        jedis.close();
    }
}
