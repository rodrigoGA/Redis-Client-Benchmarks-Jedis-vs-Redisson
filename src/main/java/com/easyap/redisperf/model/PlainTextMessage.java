package com.easyap.redisperf.model;

import org.apache.commons.lang3.RandomStringUtils;

import java.io.Serializable;
import java.util.concurrent.ThreadLocalRandom;

public class PlainTextMessage implements Serializable {

    private static final long serialVersionUID = 7L;

    private final String topic;
    private final String payload;

    public PlainTextMessage(String topic, String payload) {
        this.topic = topic;
        this.payload = payload;
    }

    public String getTopic() {
        return topic;
    }

    public String getPayload() {
        return payload;
    }

    public static PlainTextMessage random() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        String topic = "topic-" + rnd.nextInt(1, 100);
        String payload = RandomStringUtils.randomAlphanumeric(1_024);
        return new PlainTextMessage(topic, payload);
    }
}
