package io.pgforge.ledger.infrastructure.persistence.strategy;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import io.pgforge.ledger.domain.account.AccountId;
import io.pgforge.ledger.domain.account.AccountNotFoundException;
import io.pgforge.ledger.domain.account.InsufficientFundsException;
import io.pgforge.ledger.domain.account.Money;
import io.pgforge.ledger.domain.transfer.EntryJournal;
import io.pgforge.ledger.domain.transfer.TransferCommand;
import io.pgforge.ledger.domain.transfer.TransferOutcome;
import io.pgforge.ledger.domain.transfer.TransferStrategy;
import io.pgforge.ledger.domain.transfer.TransferStrategyName;
import io.pgforge.ledger.infrastructure.observability.TransferMetrics;

/**
 * Pessimistic transfer strategy — locks both rows with {@code FOR UPDATE} in
 * deterministic id order, then updates {@code balance = balance ± amount} server-side.
 *
 * <p>The single statement {@code SELECT ... WHERE id IN (?,?) ORDER BY id FOR UPDATE}
 * acquires locks in ascending id order regardless of the order parameters were passed in,
 * so two concurrent two-account transfers cannot deadlock against each other.
 */
@Service
public class JdbcPessimisticTransferStrategy implements TransferStrategy {

    private final JdbcClient jdbc;
    private final EntryJournal entryJournal;
    private final TransferMetrics metrics;
    private final TransactionTemplate txTemplate;

    public JdbcPessimisticTransferStrategy(
            JdbcClient jdbc,
            EntryJournal entryJournal,
            TransferMetrics metrics,
            PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.entryJournal = entryJournal;
        this.metrics = metrics;
        TransactionTemplate template = new TransactionTemplate(txManager);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        template.setName("ledger.transfer.pessimistic");
        this.txTemplate = template;
    }

    @Override
    public TransferStrategyName name() {
        return TransferStrategyName.PESSIMISTIC;
    }

    @Override
    public TransferOutcome execute(TransferCommand command) {
        try {
            return metrics.timerFor(name()).recordCallable(() -> doTransfer(command));
        } catch (CannotAcquireLockException e) {
            metrics.pessimisticDeadlocks().increment();
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private TransferOutcome doTransfer(TransferCommand cmd) {
        return txTemplate.execute(status -> {
            BigDecimal amount = cmd.amount().amount();

            List<BalanceRow> rows = jdbc.sql("SELECT id, balance FROM accounts "
                            + "WHERE id IN (?, ?) ORDER BY id FOR UPDATE")
                    .params(cmd.from().value(), cmd.to().value())
                    .query((rs, n) -> new BalanceRow(rs.getLong("id"), rs.getBigDecimal("balance")))
                    .list();

            BalanceRow fromRow = pickRow(rows, cmd.from());
            pickRow(rows, cmd.to());

            if (fromRow.balance().compareTo(amount) < 0) {
                metrics.insufficientFunds().increment();
                throw new InsufficientFundsException(
                        cmd.from(), Money.of(fromRow.balance()), cmd.amount());
            }

            jdbc.sql("UPDATE accounts SET balance = balance - ? WHERE id = ?")
                    .params(amount, cmd.from().value())
                    .update();
            jdbc.sql("UPDATE accounts SET balance = balance + ? WHERE id = ?")
                    .params(amount, cmd.to().value())
                    .update();

            long entryId = entryJournal.appendEntry(cmd.from(), cmd.to(), cmd.amount());
            return new TransferOutcome(entryId, cmd.from(), cmd.to(), cmd.amount(), 1);
        });
    }

    private static BalanceRow pickRow(List<BalanceRow> rows, AccountId id) {
        return rows.stream()
                .filter(r -> r.id() == id.value())
                .findFirst()
                .orElseThrow(() -> new AccountNotFoundException(id));
    }
}
