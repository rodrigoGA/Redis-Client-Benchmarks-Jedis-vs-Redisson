# Repository Guidelines

## Project Structure & Module Organization
- Purpose: benchmark Jedis vs Redisson (with/without client-side caching) under aligned serialization/pooling. Results feed the README and CSV reports for regression tracking.
- `src/main/java/com/easyap/redisperf/` — Java sources (benchmark harness, adapters, metrics, models).
- `benchmark-results/` — Generated CSV reports (`latest.csv`, `run-*.csv`) and notes.
- `Dockerfile`, `run.sh` — Containerised build/runtime workflow.
- `README.md` — High-level documentation and benchmark results.

## Build, Test, and Development Commands
- `./run.sh` — Build Maven project in Docker, start Redis, execute the benchmark, archive results, and clean up containers/images.
- `./apache-maven-3.9.6/bin/mvn -q -DskipTests compile` — Local compilation (run inside the repo if Maven is unpacked).
- `docker run --rm redis:7-alpine` — Spin up a self-managed Redis instance if needed for debugging (not required by `run.sh`).

## Coding Style & Naming Conventions
- Language: Java 17 (compiled to 1.8). Follow standard Java conventions (UpperCamelCase classes, lowerCamelCase methods/fields).
- Indentation: 4 spaces, no tabs.
- Keep comments concise; prefer English for identifiers and log messages.
- Serialization: all clients rely on Java native serialization (`SerializationCodec`, `ObjectInput/OutputStream`).

## Testing Guidelines
- Benchmark correctness is validated by `ReadMostlyBenchmark` consistency checks; failures log `Consistency=FAILED`.
- No automated unit test suite; rely on deterministic runs via `./run.sh`.
- All test scenarios and parameters **must** be documented in `README.md` (workloads, object catalogue, serialization strategy). If you add/remove scenarios or tweak settings (threads, cache size, TTL, serialization), update the README section “Results” and note the change.
- When modifying metrics or serialization logic, run at least two iterations to confirm consistency in `benchmark-results/latest-notes.txt`.

## Commit & Pull Request Guidelines
- Commits should be descriptive (imperative mood, e.g., “Add CSV export for benchmark results”).
- Squash related changes where possible; avoid committing generated files unless explicitly requested.
- Pull requests should include: summary of changes, impact on benchmark outputs, and confirmation that `./run.sh` completes successfully. Attach snippets from `benchmark-results/latest-notes.txt` if behaviour changes.

## Security & Configuration Tips
- Redis connections default to RESP3 without authentication; set `REDIS_URI` (e.g., `redis://user:password@host:6379/0`) before running in secured environments.
- `run.sh` removes Docker artefacts automatically; ensure no production containers share the `redis-benchmark-net` name to avoid collisions.
