package com.easyap.redisperf;

import com.easyap.redisperf.config.RedisEndpoint;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.Protocol;
import org.redisson.config.SingleServerConfig;
import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.RedisProtocol;
import redis.clients.jedis.csc.CacheConfig;
import redis.clients.jedis.csc.CacheFactory;

public class RedisClientFactory {

    private final String redisUri;

    public RedisClientFactory(String redisUri) {
        this.redisUri = redisUri;
    }

    public JedisPooled createJedis() {
        RedisEndpoint endpoint = RedisEndpoint.fromUri(redisUri);
        DefaultJedisClientConfig clientConfig = buildClientConfig(endpoint);
        ConnectionPoolConfig poolConfig = createPoolConfig();
        HostAndPort hostAndPort = new HostAndPort(endpoint.host(), endpoint.port());
        return new JedisPooled(hostAndPort, clientConfig, poolConfig);
    }

    public JedisPooled createCachedJedis(int maxCacheSize) {
        RedisEndpoint endpoint = RedisEndpoint.fromUri(redisUri);
        DefaultJedisClientConfig clientConfig = buildClientConfig(endpoint);
        ConnectionPoolConfig poolConfig = createPoolConfig();
        CacheConfig cacheConfig = CacheConfig.builder()
                .maxSize(maxCacheSize)
                .build();
        HostAndPort hostAndPort = new HostAndPort(endpoint.host(), endpoint.port());
        return new JedisPooled(hostAndPort, clientConfig, CacheFactory.getCache(cacheConfig), poolConfig);
    }

    public RedissonClient createRedisson() {
        Config config = new Config();
        config.setThreads(0);
        config.setNettyThreads(0);
        config.setProtocol(Protocol.RESP3);
        config.setCodec(new org.redisson.codec.SerializationCodec());

        RedisEndpoint endpoint = RedisEndpoint.fromUri(redisUri);
        String address = (endpoint.ssl() ? "rediss" : "redis") + "://" + endpoint.host() + ":" + endpoint.port();
        SingleServerConfig single = config.useSingleServer()
                .setAddress(address)
                .setDatabase(endpoint.database())
                .setRetryAttempts(4)
                .setRetryInterval(1500)
                .setTimeout(3000)
                .setPingConnectionInterval(30_000)
                .setKeepAlive(true)
                .setConnectionPoolSize(32)
                .setConnectionMinimumIdleSize(2)
                .setSubscriptionConnectionPoolSize(16)
                .setSubscriptionConnectionMinimumIdleSize(2);

        if (endpoint.password() != null) {
            single.setPassword(endpoint.password());
        }

        return Redisson.create(config);
    }

    private static DefaultJedisClientConfig buildClientConfig(RedisEndpoint endpoint) {
        DefaultJedisClientConfig.Builder builder = DefaultJedisClientConfig.builder()
                .protocol(RedisProtocol.RESP3)
                .database(endpoint.database())
                .ssl(endpoint.ssl());

        if (endpoint.password() != null) {
            builder.password(endpoint.password());
        }
        return builder.build();
    }

    private static ConnectionPoolConfig createPoolConfig() {
        ConnectionPoolConfig poolConfig = new ConnectionPoolConfig();
        poolConfig.setMaxTotal(32);
        poolConfig.setMaxIdle(16);
        poolConfig.setMinIdle(2);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setBlockWhenExhausted(true);
        return poolConfig;
    }
}
