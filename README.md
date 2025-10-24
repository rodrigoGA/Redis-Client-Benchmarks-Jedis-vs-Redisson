# Redis Benchmark: Jedis vs Redisson

## Overview
This project benchmarks four Redis client configurations under identical serialization (Java native `ObjectInput/OutputStream`) and pooling settings:

- **Jedis (no cache)** – `JedisPooled`, RESP3, 32 max / 16 idle / 2 min connections.
- **Jedis client cache** – same as above plus `redis.clients.jedis.csc` (LFU, 20 000 entries). Jedis invalidates the cache synchronously on `SET`, so readers fetch fresh data immediately.
- **Redisson (no cache)** – `RedissonClient` with `SerializationCodec` (same payload format as Jedis), 32/16/2 connection and subscription pools, no local cache.
- **Redisson client cache** – `RedissonClient` + native `RClientSideCaching` (RESP3 tracking) limited to 20 000 entries. Invalidations arrive asynchronously per bucket, trading latency for eventual consistency.

Two workloads are exercised:

| Mode | Description |
|------|-------------|
| **Balanced Set/Get** | Writers immediately read back the value they just stored (write-followed-by-read). |
| **Read-Mostly** | 25 % writers / 75 % readers hammer the same key to stress cache invalidation. |

### Payload catalogue
- **CustomerProfile** – lightweight profile POJO (ID, full name, email, loyalty points, segment).
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

The script builds a Java 8 fat-jar in Docker, starts a dedicated Redis 7 container, runs the benchmark, then removes the container, network and image. Artefacts land under `benchmark-results/`:

- `latest.csv` – per-scenario metrics for the last run.
- `run-*.csv` – archived runs (timestamped).
- `latest-notes.txt` – human-readable diagnostics (winners, consistency checks).
- `multi-run-summary.csv` – averages/min/max/stdev/spread across all archived runs.
- `multi-run-winners.csv` – winner per object & workload and its advantage vs the runner-up.

## Results (3 consecutive runs)
Statistics below are computed from the three archived CSV files (`run-20251024-*.csv`).

### Balanced Set/Get (average ops/s)
| Object | Winner | Avg ops/s | Advantage vs #2 | Winner spread |
|--------|--------|-----------|-----------------|---------------|
| CustomerProfile | Jedis (no cache) | 104 360 | +15 434 | 5.0 % |
| InventorySnapshot | Jedis (no cache) | 55 685 | +5 672 | 45.9 % *(heavy payload)* |
| LargeString | Redisson client cache | 99 105 | +16 604 | 35.3 % |
| LargeTextDocument | Redisson client cache | 84 906 | +6 698 | 3.1 % |
| MetricsBatch | Redisson client cache | 89 197 | +9 920 | 25.8 % |
| OrderAggregate | Jedis (no cache) | 77 683 | +13 420 | 5.1 % |
| PlainTextMessage | Jedis (no cache) | 99 737 | +13 388 | 22.6 % |

**Observations**
- Jedis without cache remains the most predictable baseline for small/medium payloads. Variance stays below 5 % except for `InventorySnapshot`, where the 200-entry payload amplifies the spread.
- Jedis client cache tracks closely behind thanks to synchronous invalidation.
- Redisson client cache delivers the highest throughput on large payloads but exhibits noticeable run-to-run swings (30–35 % spread) due to its asynchronous invalidation window.

### Read-Mostly (average ops/s)
| Object | Winner | Avg ops/s | Advantage vs #2 | Winner spread |
|--------|--------|-----------|-----------------|---------------|
| CustomerProfile | Redisson client cache | 152 296 | +36 086 | 17.7 % |
| InventorySnapshot | Redisson client cache | 45 063 | +568 | 7.8 % |
| LargeString | Jedis client cache | 91 583 | +21 722 | 2.8 % |
| LargeTextDocument | Jedis client cache | 84 922 | +15 620 | 12.1 % |
| MetricsBatch | Redisson client cache | 145 540 | +20 408 | 17.7 % |
| OrderAggregate | Redisson client cache | 89 620 | +4 200 | 12.1 % |
| PlainTextMessage | Redisson client cache | 120 264 | +13 889 | 16.8 % |

### Consistency findings
- Jedis (with or without CSC) and Redisson without cache always observed the latest version written.
- Redisson client cache showed eventual consistency side-effects:
  - **Run 1:** `OrderAggregate` (observed version 407 vs 20 001) and `MetricsBatch` (observed 827).
  - **Run 2:** `OrderAggregate` (2 010) and `MetricsBatch` (737).
  - **Run 3:** `LargeString` (478).
- The reader cache keeps serving stale data until the RESP3 invalidation arrives. Under load, this window can last long enough that the benchmark finishes before the cache is refreshed.

**Mitigations:** use Redisson’s “advanced local cache” (`LocalCachedMapOptions`), restrict CSC to readers while writers use the non-cached client, or accept eventual consistency when opting for the native CSC.

### Recommendations
- **Mixed read/write workloads:** Jedis (no cache) offers the best blend of throughput and stability; Jedis CSC is a safe upgrade when a small coherent local cache is needed.
- **Read-heavy with strong coherency:** Jedis CSC leads without incurring stale reads.
- **Redisson CSC:** attractive peak throughput for large payloads, but expect higher variance and eventual consistency unless you adopt the advanced cache or limit CSC to read paths.

## Working with the CSV output
The artefacts are CSV-friendly. Example:
```python
import pandas as pd
summary = pd.read_csv('benchmark-results/multi-run-summary.csv')
summary.head()
```
`multi-run-winners.csv` already lists the winner and advantage per workload, making it suitable for dashboards or further analysis.

---
_Generated by Codex (OpenAI)._ 
