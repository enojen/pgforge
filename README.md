# pgforge

A sandbox for reproducing real-world PostgreSQL problems and proving the fixes with reproducible benchmarks.

## Description

pgforge is a hands-on environment for exploring how PostgreSQL behaves under stress. Each experiment starts with a broken or naive baseline, layers fixes on top, and measures the difference at every step. Every claim is backed by a benchmark that runs on a single laptop via Docker Compose.

The repo ships with a small example domain (a minimal double-entry ledger) used as a playground, but the framework itself is generic and can be reused with any PostgreSQL-backed service.

pgforge is inspired in part by [OpenAI's "Scaling PostgreSQL to the Next Level"](https://pigsty.io/blog/db/openai-pg/) — it covers a subset of the same patterns at laptop scale, with reproducible benchmarks for each.

### Experiments

| #   | Topic                              | What it shows                                                 |
| --- | ---------------------------------- | ------------------------------------------------------------- |
| 1   | Concurrency control                | Naive vs. pessimistic vs. optimistic locking under contention |
| 2   | Connection pooling                 | Direct connections vs. HikariCP vs. PgBouncer at varying load |
| 3   | Read/write split                   | Routing reads to replicas: when it helps, when it hurts       |
| 4   | Indexing strategies                | B-tree, partial, and covering indexes on a 1M+ row dataset    |
| 5   | Idempotency                        | Production-quality idempotency keys with proper edge cases    |
| 6   | Workload separation                | Protecting critical paths from background-job interference    |
| 7   | The "bad ORM query" disaster       | Recreating a real-world incident and fixing it                |
| 8   | Statement timeouts & query killing | Protecting the database from runaway queries                  |
| 9   | Online schema migrations           | Safe migrations: lock timeouts, column adds, table rewrites   |
| 10  | Application-level rate limiting    | Token bucket and leaky bucket patterns in front of the DB     |

Each experiment lives in `docs/` with its own setup, runnable benchmark, and trade-off analysis.

### Tech stack

- Java 25 (LTS), Spring Boot 4.0
- PostgreSQL 18
- HikariCP (in-app pooling), PgBouncer (external pooling)
- Micrometer → Prometheus → Grafana
- [k6](https://k6.io/) for load testing
- Docker Compose for orchestration

## Badges

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java 25](https://img.shields.io/badge/Java-25-blue.svg)](https://openjdk.org/projects/jdk/25/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-green.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-336791.svg)](https://www.postgresql.org/)

## Installation

### Requirements

- Docker and Docker Compose
- [k6](https://k6.io/docs/get-started/installation/) for running benchmarks
- (Optional) Java 25 and Gradle if you want to build the app outside Docker

### Setup

```bash
git clone https://github.com/<your-username>/pgforge.git
cd pgforge
docker compose up
```

Wait ~30 seconds for the stack to come up. Once it's ready:

- App: http://localhost:8080
- Grafana: http://localhost:3000 (admin / admin)
- Prometheus: http://localhost:9090

## Usage

Once the stack is up, the app exposes a Prometheus scrape endpoint at `/actuator/prometheus` and a basic Grafana dashboard (HTTP throughput, HikariCP pool, app health) is provisioned out of the box.

Experiments will be runnable via k6 once shipped:

```bash
k6 run benchmarks/<NN>-<topic>.js
```

Each experiment script will write a summary report to `benchmarks/results/`. Reports follow the shape:

| Strategy    | Throughput | p95  | p99  | Correct?          |
| ----------- | ---------- | ---- | ---- | ----------------- |
| Naive       | high       | low  | low  | ❌ Drift detected |
| Pessimistic | moderate   | high | high | ✅                |
| Optimistic  | high       | low  | mod. | ✅ (with retries) |

The above is illustrative, not measured. Numbers vary by hardware — the point is the trade-offs, not absolute values.

## Support

Open an [issue](https://github.com/<your-username>/pgforge/issues) for bugs, questions, or experiment ideas.

## Roadmap

- [ ] Experiment 1: Concurrency control
- [ ] Experiment 2: Connection pooling
- [ ] Experiment 3: Read/write split
- [ ] Experiment 4: Indexing strategies
- [ ] Experiment 5: Idempotency
- [ ] Experiment 6: Workload separation
- [ ] Experiment 7: The bad-ORM-query disaster
- [ ] Experiment 8: Statement timeouts & query killing
- [ ] Experiment 9: Online schema migrations
- [ ] Experiment 10: Application-level rate limiting
- [ ] (Stretch) Experiment 11: Outbox pattern with Debezium
- [ ] (Stretch) Experiment 12: Saga vs. distributed transactions

## Contributing

Contributions are welcome. Useful directions:

- Corrections, especially around PostgreSQL internals
- New experiments or alternative approaches to existing ones
- Different example domains (inventory, queues, counters)
- Improved Grafana dashboards
- Translations of the docs

Please keep claims grounded — every assertion should be backed by a benchmark or a citation. For larger changes, open an issue first to discuss.

## License

[MIT](https://choosealicense.com/licenses/mit/)
