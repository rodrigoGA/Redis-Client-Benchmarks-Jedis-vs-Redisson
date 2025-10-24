# Redis Benchmark: Jedis vs Redisson

## Overview
This project benchmarks four Redis client configurations under identical serialization and pooling settings:

- **Jedis (no cache)** – manual Java serialization (`ObjectOutputStream`) with `JedisPooled` (RESP3, 32 max / 16 idle / 2 min connections).
- **Jedis client cache** – same as above plus `redis.clients.jedis.csc` local cache (LFU, 20 000 entries). Jedis invalidates the entry synchronously when writing, so readers always fetch the latest value on the next access.
- **Redisson SerializationCodec** – `RedissonClient` using `SerializationCodec` (RESP3) without any local cache; pools mirror the Jedis setup and subscription pools are sized accordingly.
- **Redisson client cache** – `RedissonClient` plus the native `RClientSideCaching` (RESP3 client tracking) with a 20 000-entry cache. Invalidations are delivered asynchronously per bucket, which provides lower latency but only eventual consistency.

Two workloads are exercised:

| Mode | Description |
|------|-------------|
| **Balanced Set/Get** | Writers immediately read back the value they just stored (write-followed-by-read). |
| **Read-Mostly** | 25 % writers / 75 % readers hammer the same key to stress cache invalidation. |

All scenarios serialize the same seven object types and share the same connection pools. Local caches (where enabled) have a hard limit of 20 000 entries.

### Payload catalogue
- **CustomerProfile** – lightweight POJO (ID, full name, email, loyalty points, segment).
- **OrderAggregate** – customer plus 3–11 order lines (SKU, units, price).
- **InventorySnapshot** – map of ~200 SKUs to stock counts.
- **PlainTextMessage** – 1 KB alphanumeric payload with topic metadata.
- **LargeTextDocument** – ~2 KB text body with title and timestamp.
- **MetricsBatch** – array of 256 doubles (metric samples) with source and timestamp.
- **LargeString** – raw 2 KB alphabetic string (serialization-only stressor).

## Running
```bash
./run.sh
```

The script builds a Java 8 fat-jar in Docker, spins up a dedicated Redis 7 container, runs the benchmark, then automatically removes the container, network and image. Artefacts are emitted to `benchmark-results/`:

- `latest.csv` – per-scenario metrics for the last run.
- `run-*.csv` – archived runs (timestamped).
- `latest-notes.txt` – human-readable diagnostics (winners, consistency findings, raw logs).
- `multi-run-summary.csv` – averages/min/max/stdev/spread across all archived runs.
- `multi-run-winners.csv` – winner per object & workload along with the throughput advantage.

## Results (3 consecutive runs)
Statistics below are computed from the three archived CSV files in `benchmark-results/run-*.csv`.

### Balanced Set/Get (average ops/s)
| Object | Winner | Avg ops/s | Advantage vs #2 | Winner spread |
|--------|--------|-----------|-----------------|---------------|
| CustomerProfile | Jedis (no cache) | 101 914 | +1 040 | 2.9 % |
| InventorySnapshot | Jedis (no cache) | 53 340 | +6 344 | 38.6 % (payload-heavy) |
| LargeString | Redisson client cache | 99 803 | +21 640 | 37.0 % |
| LargeTextDocument | Redisson client cache | 91 628 | +15 081 | 53.4 % |
| MetricsBatch | Redisson client cache | 83 169 | +5 431 | 5.3 % |
| OrderAggregate | Jedis (no cache) | 77 146 | +11 500 | 5.2 % |
| PlainTextMessage | Redisson client cache | 97 152 | +1 066 | 10.9 % |

Key observations:
- Jedis without cache remains the most predictable baseline for small/medium payloads (≤5 % spread except on the 200-entry snapshot).
- Jedis client cache tracks closely behind with ≤10 % spread thanks to synchronous entry invalidation.
- Redisson client cache reaches the highest throughput on large payloads but shows significant run-to-run variance (30–53 % spread), reflecting its asynchronous invalidation window.

### Read-Mostly (average ops/s)
| Object | Winner | Avg ops/s | Advantage vs #2 | Winner spread |
|--------|--------|-----------|-----------------|---------------|
| CustomerProfile | Redisson client cache | 160 573 | +30 560 | 3.6 % |
| InventorySnapshot | Jedis (no cache) | 43 996 | +109 | 5.1 % |
| LargeString | Jedis client cache | 93 333 | +15 230 | 15.2 % |
| LargeTextDocument | Jedis client cache | 83 960 | +7 095 | 6.2 % |
| MetricsBatch | Redisson client cache | 154 302 | +36 258 | 23.7 % |
| OrderAggregate | Redisson client cache | 103 941 | +17 659 | 22.8 % |
| PlainTextMessage | Jedis client cache | 113 903 | +11 614 | 7.3 % |

### Consistency findings
- Jedis (with or without CSC) and Redisson without cache always observed the latest version.
- Redisson client cache served stale values for **CustomerProfile**, **PlainTextMessage**, and **LargeTextDocument** in every run (`latest-notes.txt` lists observed versions thousands behind the final write). The native `RClientSideCaching` updates reader caches asynchronously; under heavy churn the invalidation message arrives late, so the reader continues to serve the old payload.


### Recommendations
- **Mixed read/write** → Jedis (no cache) for peak throughput and predictability; Jedis CSC if you need a small coherent local cache.
- **Read-heavy with strong coherency** → Jedis CSC outperforms without breaking consistency.
- **Redisson CSC** → impressive throughput on large payloads, but expect high variance and eventual consistency unless you adopt the advanced cache or confine CSC to read paths.


---
_Generated by Codex (OpenAI)._ 
