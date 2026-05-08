# Experiment 1: Concurrency Control

## Problem

Two concurrent transfers from the same account can lose money under naive read-then-write
semantics. PostgreSQL's `READ COMMITTED` does not protect against this lost-update anomaly
([PG18 docs: Read Committed Isolation Level][pg-rc]). We demonstrate the bug, fix it two
different ways (pessimistic locking and optimistic CAS), and measure the trade-offs with
a reproducible benchmark.

## Schema

The ledger schema is defined in [`V01__ledger_schema.sql`](../src/main/resources/db/migration/V01__ledger_schema.sql).

- `accounts(id, name, balance, version, created_at)` — the `version` column carries the
  optimistic-locking counter; pessimistic and naive strategies do not touch it.
- `entries(id, from_account_id, to_account_id, amount, created_at)` — append-only journal
  of every committed transfer. `amount > 0` is enforced via a CHECK.
- [`V02__exp01_indexes.sql`](../src/main/resources/db/migration/V02__exp01_indexes.sql) adds
  the missing FK indexes on `entries.from_account_id` and `entries.to_account_id`.

The double-entry property is invariant: `SUM(accounts.balance) == SUM(initial_deposits)`.

## Architecture

The ledger module is laid out in strict hexagonal layers:

- `domain/` — pure POJO entities, value objects, ports. No Spring, no Jakarta.
- `application/` — pure POJO use cases (`TransferService`, `AccountService`, `RetryPolicy`)
  wired manually via `infrastructure/config/LedgerBeansConfig`.
- `infrastructure/` — every adapter: JDBC repositories, the three locking strategies,
  Spring MVC controllers, Micrometer metrics, exception handling.

Strategy classes implement the `TransferStrategy` port (`domain/transfer/TransferStrategy.java`)
and live in `infrastructure/persistence/strategy/`. A new strategy is one file plus one
entry in `LedgerBeansConfig`.

### Why `SUM(balance)` is not a sufficient bug detector

A subtle pitfall: under bidirectional contention against a small account set, naive's
lost updates are often *symmetric* — the same stale literal is written to both the debit
and credit side, so `SUM(balance)` stays equal to the initial total even though both
accounts disagree with the journal. The bug shows up reliably only in
`balance == initial − Σ(out) + Σ(in)` on a per-account basis. The Layer 3 IT runs
unidirectional traffic against two accounts so the reconciliation drift is unambiguous.

## Strategy 1 — Naive (broken baseline)

[`JdbcNaiveTransferStrategy`](../src/main/java/io/pgforge/ledger/infrastructure/persistence/strategy/JdbcNaiveTransferStrategy.java)
preserves the lost-update bug deliberately:

```sql
SELECT balance FROM accounts WHERE id = ?;          -- no row lock
SELECT balance FROM accounts WHERE id = ?;          -- no row lock
-- application computes new_from = balance - amount, new_to = balance + amount
UPDATE accounts SET balance = ? WHERE id = ?;       -- literal value!
UPDATE accounts SET balance = ? WHERE id = ?;       -- literal value!
INSERT INTO entries (...) VALUES (...);
```

Two concurrent transfers from the same account both read `balance = 1000`, both compute
`900`, and both `UPDATE balance = 900`. One update is silently lost, the ledger sum drifts.
This race exists under READ COMMITTED because each statement only sees committed data
*as of its own start time* and there is no row lock to serialise the read-modify-write.

## Strategy 2 — Pessimistic locking

[`JdbcPessimisticTransferStrategy`](../src/main/java/io/pgforge/ledger/infrastructure/persistence/strategy/JdbcPessimisticTransferStrategy.java)
acquires row-level locks before reading, in deterministic id order:

```sql
SELECT id, balance FROM accounts
 WHERE id IN (?, ?)
 ORDER BY id
   FOR UPDATE;                                       -- locks both rows
UPDATE accounts SET balance = balance - ? WHERE id = ?;
UPDATE accounts SET balance = balance + ? WHERE id = ?;
INSERT INTO entries (...) VALUES (...);
```

`ORDER BY id FOR UPDATE` in a single statement guarantees lock acquisition order is
ascending by id, regardless of how the application passes the parameters. Two concurrent
two-account transfers therefore cannot deadlock. Read-modify-write happens server-side
(`balance = balance ± :amt`) under the lock, so the literal-value race from naive cannot
recur.

Trade-offs: contention serialises through the lock — throughput is bounded by
how long the txn holds the row. Long-running transactions block everyone else.

## Strategy 3 — Optimistic (CAS on `version`)

[`JdbcOptimisticTransferStrategy`](../src/main/java/io/pgforge/ledger/infrastructure/persistence/strategy/JdbcOptimisticTransferStrategy.java)
uses compare-and-set on the `version` column with a bounded retry loop **outside** the
transaction boundary:

```sql
SELECT id, balance, version FROM accounts WHERE id IN (?, ?);
-- application validates balance
UPDATE accounts SET balance = balance - ?, version = version + 1
 WHERE id = ? AND version = ?;                       -- CAS on FROM
UPDATE accounts SET balance = balance + ?, version = version + 1
 WHERE id = ? AND version = ?;                       -- CAS on TO
INSERT INTO entries (...) VALUES (...);
```

If either CAS updates zero rows, an internal `OptimisticConflictException` rolls the
transaction back, and the loop sleeps a jittered backoff before opening a fresh
transaction (defaults: 5 attempts, 2 ms → 50 ms). Each retry is observable via the
`ledger.transfer.retries` counter; exhaustion via `ledger.transfer.exhaustions`.

Trade-offs: no locks, so happy-path throughput is high; cost shows up as retries when
contention rises. Under heavy contention on a small key space the retry rate can dominate.

## Benchmark setup

- Run **direct against Postgres** (port 5432) via the `direct` Spring profile:
  `SPRING_PROFILES_ACTIVE=direct docker compose up --build`. PgBouncer-fronted runs are
  Experiment 2's concern.
- 20 accounts seeded with 10 000 each → expected total 200 000.
- Three k6 scenarios, each: ramp 0 → 50 VUs over 30 s, hold for 60 s, ramp down for 10 s.
  Scenarios run sequentially (`startTime`) so they don't pollute each other's accounts.
- Each iteration POSTs a transfer of `amount ∈ [1, 100]` between two random distinct
  accounts.
- Setup truncates the ledger via `POST /admin/reset` (gated by
  `pgforge.admin.enabled=true` in the `direct` and `local` profiles).
- `teardown()` GETs `/ledger/sum` and checks `SUM(balance) == 200 000`. Note:
  with bidirectional transfers across many accounts this catches naive's drift
  probabilistically (asymmetric lost-update overlaps move money). The rigorous
  per-account reconciliation invariant — `account.balance == initial − Σ(out) + Σ(in)`
  — lives in `TransferConcurrencyIT` (unidirectional, deterministic detection).

Run: `k6 run benchmarks/01-concurrency.js`. Results land in
`benchmarks/results/01-concurrency-<timestamp>.{json,md}`.

## Results

_Empty until the first benchmark run; numbers are filled in from the `*.md` produced by k6
(per `AGENTS.md`: every claim must be backed by a benchmark or citation — placeholders are
forbidden). The Correct? column is a property of the algorithm, not a measurement, so it
ships pre-filled._

| Strategy | Throughput | p95 | p99 | Correct? |
|---|---|---|---|---|
| Naive       | _tbd_ | _tbd_ | _tbd_ | ❌ Drift detected |
| Pessimistic | _tbd_ | _tbd_ | _tbd_ | ✅ |
| Optimistic  | _tbd_ | _tbd_ | _tbd_ | ✅ (with retries) |

## Trade-off discussion

_To be filled in after the first run, grounded in the table above. Expected shape: naive
is fastest but wrong; pessimistic is the safest baseline; optimistic wins under low
contention and degrades under high contention as the retry rate grows._

## Future extensions (out of scope here)

- A fourth strategy at `REPEATABLE READ` or `SERIALIZABLE` isolation — same SQL as
  pessimistic but lets PG drive the conflict detection. Different operational profile;
  worth a side-by-side once Experiment 1 is published.
- PgBouncer-fronted runs of the same three strategies → Experiment 2.
- Statement-timeout and lock-timeout interactions → Experiment 8.

## Citations

- PostgreSQL 18 — *Concurrency Control → Transaction Isolation → Read Committed Isolation
  Level*. <https://www.postgresql.org/docs/18/transaction-iso.html#XACT-READ-COMMITTED>
- PostgreSQL 18 — *Concurrency Control → Explicit Locking → Row-Level Locks → FOR UPDATE*.
  <https://www.postgresql.org/docs/18/explicit-locking.html#LOCKING-ROWS>
- PostgreSQL 18 — *Concurrency Control → Serialization Failure Handling* (SQLSTATE 40001 /
  40P01). <https://www.postgresql.org/docs/18/mvcc-serialization-failure-handling.html>

[pg-rc]: https://www.postgresql.org/docs/18/transaction-iso.html#XACT-READ-COMMITTED
