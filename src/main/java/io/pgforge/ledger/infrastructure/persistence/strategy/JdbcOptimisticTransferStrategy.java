package io.pgforge.ledger.infrastructure.persistence.strategy;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import io.pgforge.ledger.application.transfer.retry.RetryPolicy;
import io.pgforge.ledger.domain.account.AccountId;
import io.pgforge.ledger.domain.account.AccountNotFoundException;
import io.pgforge.ledger.domain.account.InsufficientFundsException;
import io.pgforge.ledger.domain.account.Money;
import io.pgforge.ledger.domain.transfer.EntryJournal;
import io.pgforge.ledger.domain.transfer.OptimisticRetryExhaustedException;
import io.pgforge.ledger.domain.transfer.TransferCommand;
import io.pgforge.ledger.domain.transfer.TransferOutcome;
import io.pgforge.ledger.domain.transfer.TransferStrategy;
import io.pgforge.ledger.domain.transfer.TransferStrategyName;
import io.pgforge.ledger.infrastructure.observability.TransferMetrics;

/**
 * Optimistic transfer strategy — compare-and-set on {@code accounts.version} with
 * a bounded retry loop that lives <strong>outside</strong> the transaction boundary.
 * Each attempt opens a fresh transaction via {@link TransactionTemplate}; on CAS
 * failure the transaction is rolled back and the loop sleeps a jittered backoff
 * before the next attempt. Retries are exhausted into
 * {@link OptimisticRetryExhaustedException}.
 */
@Service
public class JdbcOptimisticTransferStrategy implements TransferStrategy {

    private final JdbcClient jdbc;
    private final EntryJournal entryJournal;
    private final TransferMetrics metrics;
    private final RetryPolicy retryPolicy;
    private final TransactionTemplate txTemplate;

    public JdbcOptimisticTransferStrategy(
            JdbcClient jdbc,
            EntryJournal entryJournal,
            TransferMetrics metrics,
            RetryPolicy retryPolicy,
            PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.entryJournal = entryJournal;
        this.metrics = metrics;
        this.retryPolicy = retryPolicy;
        TransactionTemplate template = new TransactionTemplate(txManager);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        template.setName("ledger.transfer.optimistic");
        this.txTemplate = template;
    }

    @Override
    public TransferStrategyName name() {
        return TransferStrategyName.OPTIMISTIC;
    }

    @Override
    public TransferOutcome execute(TransferCommand command) {
        try {
            return metrics.timerFor(name()).recordCallable(() -> retryingTransfer(command));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private TransferOutcome retryingTransfer(TransferCommand cmd) {
        for (int attempt = 1; attempt <= retryPolicy.maxAttempts(); attempt++) {
            try {
                return attemptTransfer(cmd, attempt);
            } catch (OptimisticConflictException e) {
                metrics.optimisticRetries().increment();
                if (attempt == retryPolicy.maxAttempts()) {
                    metrics.optimisticExhaustions().increment();
                    throw new OptimisticRetryExhaustedException(attempt);
                }
                sleepBackoff(retryPolicy.backoff(attempt));
            }
        }
        throw new IllegalStateException("retry loop exited without return");
    }

    private TransferOutcome attemptTransfer(TransferCommand cmd, int attempt) {
        return txTemplate.execute(status -> {
            BigDecimal amount = cmd.amount().amount();

            List<VersionedBalanceRow> rows = jdbc.sql(
                            "SELECT id, balance, version FROM accounts WHERE id IN (?, ?)")
                    .params(cmd.from().value(), cmd.to().value())
                    .query((rs, n) -> new VersionedBalanceRow(
                            rs.getLong("id"), rs.getBigDecimal("balance"), rs.getLong("version")))
                    .list();

            VersionedBalanceRow fromRow = pickRow(rows, cmd.from());
            VersionedBalanceRow toRow = pickRow(rows, cmd.to());

            if (fromRow.balance().compareTo(amount) < 0) {
                metrics.insufficientFunds().increment();
                throw new InsufficientFundsException(
                        cmd.from(), Money.of(fromRow.balance()), cmd.amount());
            }

            int fromUpdated = jdbc.sql("UPDATE accounts "
                            + "SET balance = balance - ?, version = version + 1 "
                            + "WHERE id = ? AND version = ?")
                    .params(amount, cmd.from().value(), fromRow.version())
                    .update();
            if (fromUpdated == 0) {
                throw new OptimisticConflictException("from version mismatch on account " + cmd.from().value());
            }

            int toUpdated = jdbc.sql("UPDATE accounts "
                            + "SET balance = balance + ?, version = version + 1 "
                            + "WHERE id = ? AND version = ?")
                    .params(amount, cmd.to().value(), toRow.version())
                    .update();
            if (toUpdated == 0) {
                throw new OptimisticConflictException("to version mismatch on account " + cmd.to().value());
            }

            long entryId = entryJournal.appendEntry(cmd.from(), cmd.to(), cmd.amount());
            return new TransferOutcome(entryId, cmd.from(), cmd.to(), cmd.amount(), attempt);
        });
    }

    private static VersionedBalanceRow pickRow(List<VersionedBalanceRow> rows, AccountId id) {
        return rows.stream()
                .filter(r -> r.id() == id.value())
                .findFirst()
                .orElseThrow(() -> new AccountNotFoundException(id));
    }

    private static void sleepBackoff(Duration backoff) {
        try {
            Thread.sleep(backoff);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("retry interrupted", e);
        }
    }
}
