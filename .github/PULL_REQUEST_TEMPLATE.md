## Summary

<!-- 1–3 sentences. What changed and why. -->

## What changed

<!-- Bullet list of the actual changes. -->

## Benchmark numbers (if applicable)

| Strategy | Throughput | p95 | p99 | Correct? |
| -------- | ---------- | --- | --- | -------- |
|          |            |     |     |          |

<!-- Drop this section for non-experiment changes. -->

## Checklist

- [ ] Every claim in the docs or code is backed by a benchmark or citation.
- [ ] If this PR adds an experiment: doc in `docs/`, k6 script in `benchmarks/`, Grafana panel or query.
- [ ] If this PR adds or changes SQL: Flyway migration is `V<NN>__<description>.sql` and ordered correctly.
- [ ] Every `@Transactional` declares isolation explicitly.
- [ ] `statement_timeout` / `lock_timeout` are set per session where relevant.
- [ ] `./gradlew spotlessCheck build test` passes locally.
- [ ] No DB mocking; integration tests use Testcontainers Postgres 18.
