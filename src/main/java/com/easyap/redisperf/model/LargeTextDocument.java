package com.easyap.redisperf.model;

import org.apache.commons.lang3.RandomStringUtils;

import java.io.Serializable;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

public class LargeTextDocument implements Serializable {

    private static final long serialVersionUID = 5L;

    private final String documentId;
    private final String title;
    private final String body;
    private final Instant publishedAt;

    public LargeTextDocument(String documentId, String title, String body, Instant publishedAt) {
        this.documentId = documentId;
        this.title = title;
        this.body = body;
        this.publishedAt = publishedAt;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public static LargeTextDocument random() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        String id = "DOC-" + rnd.nextLong(1_000_000_000L);
        String title = RandomStringUtils.randomAlphabetic(40);
        String body = RandomStringUtils.randomAlphabetic(2_048);
        Instant publishedAt = Instant.now().minusSeconds(rnd.nextLong(0, 86_400L * 30));
        return new LargeTextDocument(id, title, body, publishedAt);
    }
}
