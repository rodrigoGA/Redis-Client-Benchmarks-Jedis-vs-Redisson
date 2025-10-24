package com.easyap.redisperf.model;

import org.apache.commons.lang3.RandomStringUtils;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class OrderAggregate implements Serializable {

    private static final long serialVersionUID = 2L;

    private final String orderId;
    private final CustomerProfile customer;
    private final List<OrderLine> orderLines;
    private final BigDecimal totalAmount;

    public OrderAggregate(String orderId, CustomerProfile customer, List<OrderLine> orderLines, BigDecimal totalAmount) {
        this.orderId = orderId;
        this.customer = customer;
        this.orderLines = orderLines;
        this.totalAmount = totalAmount;
    }

    public String getOrderId() {
        return orderId;
    }

    public CustomerProfile getCustomer() {
        return customer;
    }

    public List<OrderLine> getOrderLines() {
        return orderLines;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public static OrderAggregate random() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        String orderId = "O" + rnd.nextLong(100_000_000L);
        CustomerProfile customer = CustomerProfile.random();
        int lineCount = rnd.nextInt(3, 12);
        List<OrderLine> lines = new ArrayList<>(lineCount);
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < lineCount; i++) {
            OrderLine line = OrderLine.random();
            lines.add(line);
            total = total.add(line.getLineAmount());
        }
        return new OrderAggregate(orderId, customer, lines, total);
    }

    public static class OrderLine implements Serializable {

        private static final long serialVersionUID = 3L;

        private final String sku;
        private final int units;
        private final BigDecimal unitPrice;

        public OrderLine(String sku, int units, BigDecimal unitPrice) {
            this.sku = sku;
            this.units = units;
            this.unitPrice = unitPrice;
        }

        public String getSku() {
            return sku;
        }

        public int getUnits() {
            return units;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        public BigDecimal getLineAmount() {
            return unitPrice.multiply(BigDecimal.valueOf(units));
        }

        public static OrderLine random() {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            String sku = RandomStringUtils.randomAlphanumeric(10).toUpperCase();
            int units = rnd.nextInt(1, 20);
            BigDecimal price = BigDecimal.valueOf(rnd.nextDouble(10.0, 500.0)).setScale(2, RoundingMode.HALF_UP);
            return new OrderLine(sku, units, price);
        }

        @Override
        public String toString() {
            return "OrderLine{" +
                    "sku='" + sku + '\'' +
                    ", units=" + units +
                    ", unitPrice=" + unitPrice +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "OrderAggregate{" +
                "orderId='" + orderId + '\'' +
                ", customer=" + customer +
                ", orderLines=" + orderLines +
                ", totalAmount=" + totalAmount +
                '}';
    }
}
