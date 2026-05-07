# Benchmarks

[k6](https://k6.io/) scripts for each experiment. Run from the host — k6 is **not** in the Compose stack.

## Running

Bring up the stack first:

```bash
docker compose up
# wait ~30s for the stack to be ready
```

Run a benchmark:

```bash
k6 run benchmarks/<NN>-<topic>.js
```

Results land in `benchmarks/results/<NN>-<timestamp>.json` (gitignored).

## What benchmarks must do

Every benchmark must:

1. **Measure performance**: throughput, p95, p99 latency. Use k6's built-in summary or custom thresholds.
2. **Verify correctness**: at the end of the run, check an invariant against Postgres directly (e.g., `SELECT SUM(balance) FROM accounts` for a ledger). Use k6 `check()` blocks. **Throughput numbers without a correctness check are not acceptable** — see [CLAUDE.md](../CLAUDE.md).

## Hardware caveat

k6 VU count is **concurrency**, not throughput. Numbers vary by hardware. Always emphasize trade-offs over absolute values.
