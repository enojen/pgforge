# Contributing to pgforge

Thanks for considering a contribution! pgforge is a sandbox for reproducing real-world PostgreSQL problems with reproducible benchmarks. The bar to merge is one rule:

> **Every claim must be backed by a benchmark or a citation.**

If you add a recommendation, fix, or trade-off statement to docs or code, attach numbers from a runnable benchmark — never assert from intuition or training data.

## Getting started

1. Fork the repo and clone your fork.
2. Bring up the stack: `docker compose up`. Wait ~30s.
3. Run a benchmark: `k6 run benchmarks/<NN>-<topic>.js`.
4. Run the test suite: `./gradlew spotlessCheck build test`.

See [README.md](README.md) for the experiment roadmap and [CLAUDE.md](CLAUDE.md) for project conventions.

## Branching

- `main` is protected; PRs only.
- Branch naming:
  - `exp/<NN>-<topic>` — work on a specific experiment (e.g., `exp/02-pooling`).
  - `feat/<topic>` — non-experiment features (tooling, infra, docs).
  - `fix/<topic>` — bug fixes.

## Commit messages — Conventional Commits

We follow [Conventional Commits](https://www.conventionalcommits.org/) as a convention. It is not enforced by tooling, but PRs that ignore it will be asked to retitle before merge.

Format:

```
<type>(<scope>): <subject>
```

Types:

| Type         | When to use                                                          |
| ------------ | -------------------------------------------------------------------- |
| `feat`       | New feature                                                          |
| `fix`        | Bug fix                                                              |
| `docs`       | Documentation only                                                   |
| `test`       | Tests only                                                           |
| `bench`      | Benchmark added or changed                                           |
| `experiment` | New experiment scaffold                                              |
| `refactor`   | Code change that does not add a feature or fix a bug                 |
| `perf`       | Performance improvement (must include before/after numbers in body)  |
| `chore`      | Build, tooling, or housekeeping                                      |

Scope is the experiment number (`exp-02`) or area (`build`, `compose`, `ci`, `docs`).

Examples:

- `feat(exp-02): add HikariCP-only baseline benchmark`
- `bench(exp-01): record p99 under 100 VU contention`
- `fix(ledger): use NUMERIC casting in transfer SQL`
- `chore(build): bump Spring Boot to 4.0.7`

## Pull requests

- **Squash-and-merge** is the default. The PR title becomes the squash commit message — write it in Conventional Commits format.
- Use the [PR template](.github/PULL_REQUEST_TEMPLATE.md). The checklist exists to enforce the core principle.
- For experiment changes: include benchmark numbers in the PR description (Strategy / Throughput / p95 / p99 / Correct?).
- For larger changes (a new experiment, a build tool change), open an issue first to discuss.

## Code style

- Java 25, Spring Boot 4.0. Constructor injection only. Records for DTOs and value objects where it fits.
- `JdbcClient` is the default for SQL-heavy experiments. JPA only when the experiment is specifically about ORM behavior (e.g., Experiment 7).
- Every `@Transactional` declares isolation explicitly.
- `statement_timeout` and `lock_timeout` are set per session, never assumed from `postgresql.conf`.
- Spotless is the formatter (palantir-java-format). Run `./gradlew spotlessApply` before pushing.

See [CLAUDE.md](CLAUDE.md) for the full set of conventions.

## Testing

Tests are a first-class concern. The rules:

- **No mocking the database.** Integration tests run against real PostgreSQL 18 via Testcontainers (`@ServiceConnection`). H2 and embedded databases are not used.
- **Unit tests** (`*Test.java`): pure Java logic, no Spring context.
- **Integration tests** (`*IT.java`): Testcontainers Postgres 18, sliced (`@JdbcTest`) or full (`@SpringBootTest`).
- **Benchmarks** (k6, in `benchmarks/`): must verify correctness invariants (e.g., `SELECT SUM(balance) FROM accounts == 0`) at the end of the run. Performance numbers without a correctness check are not acceptable.

Run the suite: `./gradlew test`.

## Reporting bugs / proposing experiments

Use the [issue templates](.github/ISSUE_TEMPLATE/). For bugs, include the experiment number, command run, and observed vs. expected. For new experiment ideas, describe the failure mode you are reproducing and the expected fix.

## License

By contributing, you agree your contributions will be licensed under the [MIT License](LICENSE).
