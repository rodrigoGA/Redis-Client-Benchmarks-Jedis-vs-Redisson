package com.easyap.redisperf.config;

import java.net.URI;

public final class RedisEndpoint {

    private final String host;
    private final int port;
    private final String password;
    private final int database;
    private final boolean ssl;

    public RedisEndpoint(String host, int port, String password, int database, boolean ssl) {
        this.host = host;
        this.port = port;
        this.password = password;
        this.database = database;
        this.ssl = ssl;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public String password() {
        return password;
    }

    public int database() {
        return database;
    }

    public boolean ssl() {
        return ssl;
    }

    public static RedisEndpoint fromUri(String uriString) {
        URI uri = URI.create(uriString);
        String scheme = uri.getScheme();
        boolean ssl = "rediss".equalsIgnoreCase(scheme);
        String host = uri.getHost();
        if (host == null || host.trim().isEmpty()) {
            host = "127.0.0.1";
        }
        int port = uri.getPort();
        if (port == -1) {
            port = ssl ? 6380 : 6379;
        }

        String userInfo = uri.getUserInfo();
        String password = null;
        if (userInfo != null && !userInfo.trim().isEmpty()) {
            int colon = userInfo.indexOf(':');
            password = colon >= 0 ? userInfo.substring(colon + 1) : userInfo;
            if (password != null && password.trim().isEmpty()) {
                password = null;
            }
        }

        int database = 0;
        if (uri.getPath() != null && uri.getPath().length() > 1) {
            try {
                database = Integer.parseInt(uri.getPath().substring(1));
            } catch (NumberFormatException ignored) {
                database = 0;
            }
        }

        return new RedisEndpoint(host, port, password, database, ssl);
    }
}
