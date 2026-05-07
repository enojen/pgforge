# pgforge

A sandbox for reproducing real-world PostgreSQL problems and proving fixes with reproducible benchmarks. See @README.md for the full project overview and the experiment roadmap.

## Core principle

**Every claim must be backed by a benchmark or a citation.** This is the project's reason to exist. If you add a recommendation, fix, or trade-off statement to docs or code, attach numbers from a runnable benchmark — never assert from intuition or training data.

## Project shape

- Each experiment lives in `docs/<NN>-<topic>.md` with: broken/naive baseline → fixes layered on top → measurements at each step → trade-off analysis.
- Benchmarks live in `benchmarks/` as k6 scripts (`benchmarks/<NN>-<topic>.js`), and write summaries to `benchmarks/results/`.
- The example domain is a minimal double-entry ledger. It's a playground — the framework should stay generic and reusable for any PostgreSQL-backed service.

## Tech stack

- Java 25 (LTS), Spring Boot 4.0
- PostgreSQL 18
- HikariCP (in-app pooling), PgBouncer (external pooling)
- Micrometer → Prometheus → Grafana
- k6 for load testing
- Docker Compose orchestrates everything

When working with these libraries, fetch current docs via context7 rather than relying on training data — Spring Boot 4.0 and Java 25 are recent and APIs may have shifted.

## Workflow

- Bring the stack up with `docker compose up`. Wait ~30s before running benchmarks.
- Run a benchmark with `k6 run benchmarks/<NN>-<topic>.js`.
- App: `localhost:8080` · Grafana: `localhost:3000` (admin/admin) · Prometheus: `localhost:9090`.
- Prefer running the affected benchmark over the whole suite for iteration speed.

## Conventions

- Experiment numbering matches the roadmap in README.md — don't renumber existing experiments.
- Each new experiment ships with: a doc in `docs/`, a runnable benchmark in `benchmarks/`, and a Grafana panel or query that surfaces the relevant metric.
- Result tables follow the README's Experiment 1 shape: `Strategy | Throughput | p95 | p99 | Correct?`. Numbers vary by hardware — emphasize trade-offs, not absolute values.
- Use Flyway-style ordered SQL migrations when introducing schema (`V<NN>__<description>.sql`).

## Code style

- Every `@Transactional` declares isolation explicitly. Defaults make concurrency experiments unreproducible across Postgres versions and JDBC driver upgrades.
- `statement_timeout` and `lock_timeout` are set per connection or session, never assumed from `postgresql.conf`. Experiment 8 depends on this being explicit.
- When an experiment measures SQL or lock behavior, write the SQL by hand (`JdbcClient`). Reserve JPA for experiments that are specifically about ORM behavior, e.g. Experiment 7 — generated SQL hides what we're measuring.
- Every experiment exposes at least one Micrometer counter or timer that a Grafana panel reads. No metric, no merge.
- Virtual threads for request handlers; platform threads for the benchmark harness itself so scheduling is pinnable and reasoned about.

## Gotchas

- HikariCP and PgBouncer pool sizes interact: when both are in play, the in-app pool must not exceed the PgBouncer-side limit, or you'll measure pool starvation instead of the thing you wanted to measure.
- k6 VU counts are concurrency, not throughput. Don't conflate them in writeups.
- PostgreSQL 18 default `wal_level` and autovacuum settings may differ from older tutorials — check `postgresql.conf` in the compose stack before debugging unexpected behavior.

## Out of scope

- Production hardening (TLS, secrets management, HA topology). This is a laptop-scale teaching repo.
- Cloud-managed Postgres specifics (RDS, Cloud SQL). Stick to vanilla Postgres so results are reproducible.
