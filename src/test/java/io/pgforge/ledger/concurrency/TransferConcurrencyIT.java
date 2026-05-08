package io.pgforge.ledger.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import io.pgforge.TestcontainersConfig;
import io.pgforge.ledger.domain.account.AccountId;
import io.pgforge.ledger.domain.account.InsufficientFundsException;
import io.pgforge.ledger.domain.account.Money;
import io.pgforge.ledger.domain.transfer.OptimisticRetryExhaustedException;
import io.pgforge.ledger.domain.transfer.TransferCommand;
import io.pgforge.ledger.domain.transfer.TransferStrategy;
import io.pgforge.ledger.infrastructure.observability.TransferMetrics;
import io.pgforge.ledger.infrastructure.persistence.strategy.JdbcNaiveTransferStrategy;
import io.pgforge.ledger.infrastructure.persistence.strategy.JdbcOptimisticTransferStrategy;
import io.pgforge.ledger.infrastructure.persistence.strategy.JdbcPessimisticTransferStrategy;

/**
 * Layer 3 — concurrency invariant ITs. Drives concurrent unidirectional transfers
 * (all {@code alice → bob}) and asserts a per-account reconciliation invariant:
 * <pre>{@code alice.balance + sum(entries.amount) == alice.initial}</pre>
 *
 * <p>Why unidirectional and why this invariant: under bidirectional contention, naive
 * lost updates produce <em>symmetric</em> drift on both rows (one debit and one
 * credit are silently overwritten with the same stale literal), so {@code SUM(balance)}
 * stays constant and the bug hides. Reconciling each balance against the journal
 * exposes the bug: when a debit is lost, the entries say "money moved" but the
 * source's balance disagrees with the journal total.
 *
 * <p>Cooperative cancellation: workers check {@code cancelled} between iterations
 * so a deadline failure cannot leak threads into the next test method.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfig.class)
class TransferConcurrencyIT {

    private static final int THREADS = 8;
    private static final int ITERATIONS_PER_THREAD = 60;
    private static final BigDecimal ALICE_INITIAL = new BigDecimal("100000.00");
    private static final BigDecimal BOB_INITIAL = new BigDecimal("0.00");
    private static final int MAX_AMOUNT = 50;
    private static final long DEADLINE_SECONDS = 120;
    private static final long TERMINATION_GRACE_SECONDS = 30;

    @Autowired
    private JdbcNaiveTransferStrategy naive;

    @Autowired
    private JdbcPessimisticTransferStrategy pessimistic;

    @Autowired
    private JdbcOptimisticTransferStrategy optimistic;

    @Autowired
    private TransferMetrics metrics;

    @Autowired
    private JdbcClient jdbc;

    private long aliceId;
    private long bobId;

    @BeforeEach
    void resetAndSeed() {
        jdbc.sql("TRUNCATE TABLE entries, accounts RESTART IDENTITY CASCADE").update();
        aliceId = insertAccount("alice", ALICE_INITIAL);
        bobId = insertAccount("bob", BOB_INITIAL);
    }

    @Test
    void naiveStrategy_losesMoneyUnderContention() throws Exception {
        ConcurrencyResult r = runConcurrentTransfers(naive);
        // Per-account reconciliation: with the bug, balances disagree with the journal.
        assertThat(reconciliationDrift()).isPositive();
        // Sanity: the bug only shows when transfers actually ran.
        assertThat(r.successCount()).isGreaterThan(THREADS * ITERATIONS_PER_THREAD / 2);
    }

    @Test
    void pessimisticStrategy_preservesInvariantUnderContention() throws Exception {
        ConcurrencyResult r = runConcurrentTransfers(pessimistic);
        assertReconciles();
        assertThat(entryCount()).isEqualTo(r.successCount());
    }

    @Test
    void optimisticStrategy_preservesInvariantUnderContention() throws Exception {
        double retriesBefore = metrics.optimisticRetries().count();
        ConcurrencyResult r = runConcurrentTransfers(optimistic);
        double retriesAfter = metrics.optimisticRetries().count();

        assertReconciles();
        assertThat(entryCount()).isEqualTo(r.successCount());
        // Contention against a 2-account pool with N>1 threads must produce real retries —
        // otherwise the test got lucky and wasn't actually exercising conflict resolution.
        assertThat(retriesAfter - retriesBefore).isGreaterThan(0);
    }

    /**
     * Returns the absolute reconciliation drift: |alice.actual − alice.expected| where
     * alice.expected is computed purely from the entries journal. Zero on correct
     * strategies; non-zero on naive lost updates.
     */
    private BigDecimal reconciliationDrift() {
        BigDecimal aliceActual = balance(aliceId);
        BigDecimal sumOut = sumAmount("from_account_id", aliceId);
        BigDecimal sumIn = sumAmount("to_account_id", aliceId);
        BigDecimal aliceExpected = ALICE_INITIAL.subtract(sumOut).add(sumIn);
        return aliceActual.subtract(aliceExpected).abs();
    }

    private void assertReconciles() {
        assertThat(reconciliationDrift())
                .as("alice's balance must equal initial − Σ(out) + Σ(in)")
                .isEqualByComparingTo("0");
    }

    private ConcurrencyResult runConcurrentTransfers(TransferStrategy strategy) throws Exception {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS, r -> {
            Thread t = new Thread(r, "concurrency-worker");
            t.setDaemon(true);
            return t;
        });
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        AtomicInteger exhausted = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();

        for (int t = 0; t < THREADS; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    ThreadLocalRandom rnd = ThreadLocalRandom.current();
                    for (int i = 0; i < ITERATIONS_PER_THREAD && !cancelled.get(); i++) {
                        Money amount = Money.of(rnd.nextInt(1, MAX_AMOUNT + 1));
                        try {
                            strategy.execute(new TransferCommand(
                                    AccountId.of(aliceId), AccountId.of(bobId), amount));
                            success.incrementAndGet();
                        } catch (InsufficientFundsException e) {
                            insufficient.incrementAndGet();
                        } catch (OptimisticRetryExhaustedException e) {
                            exhausted.incrementAndGet();
                        } catch (RuntimeException e) {
                            other.incrementAndGet();
                        }
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        boolean finished = done.await(DEADLINE_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            cancelled.set(true);
            done.await(TERMINATION_GRACE_SECONDS, TimeUnit.SECONDS);
        }
        pool.shutdown();
        if (!pool.awaitTermination(TERMINATION_GRACE_SECONDS, TimeUnit.SECONDS)) {
            pool.shutdownNow();
            pool.awaitTermination(TERMINATION_GRACE_SECONDS, TimeUnit.SECONDS);
        }
        assertThat(finished)
                .as("all worker threads completed within %ds", DEADLINE_SECONDS)
                .isTrue();
        return new ConcurrencyResult(success.get(), insufficient.get(), exhausted.get(), other.get());
    }

    private long insertAccount(String name, BigDecimal balance) {
        return jdbc.sql("INSERT INTO accounts (name, balance) VALUES (?, ?) RETURNING id")
                .params(name, balance)
                .query(Long.class)
                .single();
    }

    private BigDecimal balance(long id) {
        return jdbc.sql("SELECT balance FROM accounts WHERE id = ?")
                .param(id)
                .query(BigDecimal.class)
                .single();
    }

    private BigDecimal sumAmount(String column, long id) {
        return jdbc.sql("SELECT COALESCE(SUM(amount), 0) FROM entries WHERE " + column + " = ?")
                .param(id)
                .query(BigDecimal.class)
                .single();
    }

    private long entryCount() {
        return jdbc.sql("SELECT count(*) FROM entries").query(Long.class).single();
    }

    private record ConcurrencyResult(
            int successCount, int insufficientCount, int exhaustedCount, int otherCount) {}
}
