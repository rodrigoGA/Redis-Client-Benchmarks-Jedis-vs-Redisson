package com.easyap.redisperf.model;

import org.apache.commons.lang3.RandomStringUtils;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class InventorySnapshot implements Serializable {

    private static final long serialVersionUID = 4L;

    private final String warehouseId;
    private final Map<String, Integer> stockBySku;

    public InventorySnapshot(String warehouseId, Map<String, Integer> stockBySku) {
        this.warehouseId = warehouseId;
        this.stockBySku = stockBySku;
    }

    public String getWarehouseId() {
        return warehouseId;
    }

    public Map<String, Integer> getStockBySku() {
        return stockBySku;
    }

    public static InventorySnapshot random() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        String warehouseId = "W" + rnd.nextInt(1, 50);
        int size = 200;
        Map<String, Integer> map = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            String sku = RandomStringUtils.randomAlphanumeric(12).toUpperCase();
            map.put(sku, rnd.nextInt(0, 10_000));
        }
        return new InventorySnapshot(warehouseId, map);
    }

    @Override
    public String toString() {
        return "InventorySnapshot{" +
                "warehouseId='" + warehouseId + '\'' +
                ", stockBySku(size)=" + stockBySku.size() +
                '}';
    }
}
