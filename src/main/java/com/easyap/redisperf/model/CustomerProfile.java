package com.easyap.redisperf.model;

import org.apache.commons.lang3.RandomStringUtils;

import java.io.Serializable;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public class CustomerProfile implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String customerId;
    private final String fullName;
    private final String email;
    private final int loyaltyPoints;
    private final String segment;

    public CustomerProfile(String customerId, String fullName, String email, int loyaltyPoints, String segment) {
        this.customerId = customerId;
        this.fullName = fullName;
        this.email = email;
        this.loyaltyPoints = loyaltyPoints;
        this.segment = segment;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public int getLoyaltyPoints() {
        return loyaltyPoints;
    }

    public String getSegment() {
        return segment;
    }

    public static CustomerProfile random() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        String id = "C" + rnd.nextLong(1_000_000_000L);
        String name = RandomStringUtils.randomAlphabetic(12) + " " + RandomStringUtils.randomAlphabetic(8);
        String email = name.toLowerCase().replace(' ', '.') + "@example.com";
        int points = rnd.nextInt(0, 50_000);
        String segment;
        switch (rnd.nextInt(4)) {
            case 0:
                segment = "Gold";
                break;
            case 1:
                segment = "Silver";
                break;
            case 2:
                segment = "Bronze";
                break;
            default:
                segment = "Platinum";
                break;
        }
        return new CustomerProfile(id, name, email, points, segment);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CustomerProfile that = (CustomerProfile) o;
        return loyaltyPoints == that.loyaltyPoints
                && Objects.equals(customerId, that.customerId)
                && Objects.equals(fullName, that.fullName)
                && Objects.equals(email, that.email)
                && Objects.equals(segment, that.segment);
    }

    @Override
    public int hashCode() {
        return Objects.hash(customerId, fullName, email, loyaltyPoints, segment);
    }

    @Override
    public String toString() {
        return "CustomerProfile{" +
                "customerId='" + customerId + '\'' +
                ", fullName='" + fullName + '\'' +
                ", email='" + email + '\'' +
                ", loyaltyPoints=" + loyaltyPoints +
                ", segment='" + segment + '\'' +
                '}';
    }
}
