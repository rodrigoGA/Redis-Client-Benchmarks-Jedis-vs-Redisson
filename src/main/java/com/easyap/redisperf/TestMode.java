package com.easyap.redisperf;

public enum TestMode {
    SET_GET("Balanced Set/Get", "Writers immediately read back the value they just stored."),
    READ_MOSTLY("Read-Mostly", "75% of threads read while 25% write to the same key to exercise invalidations.");

    private final String title;
    private final String description;

    TestMode(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }
}
