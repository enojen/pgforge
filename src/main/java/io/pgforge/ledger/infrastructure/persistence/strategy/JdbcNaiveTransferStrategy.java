package io.pgforge.ledger.infrastructure.persistence.strategy;

import java.math.BigDecimal;

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
 * Naive transfer strategy — preserves the lost-update bug by design.
 *
 * <p>Reads each balance with no row lock, computes the new balances in the application,
 * and writes them back as literal values. Under contention two concurrent transfers from
 * the same account both observe the same starting balance, both compute new = old - amount,
 * and both UPDATE with that literal — net effect: one debit is silently overwritten and the
 * ledger sum drifts. READ COMMITTED does not protect against this; only locking or a
 * version check does.
 */
@Service
public class JdbcNaiveTransferStrategy implements TransferStrategy {

    private final JdbcClient jdbc;
    private final EntryJournal entryJournal;
    private final TransferMetrics metrics;
    private final TransactionTemplate txTemplate;

    public JdbcNaiveTransferStrategy(
            JdbcClient jdbc,
            EntryJournal entryJournal,
            TransferMetrics metrics,
            PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.entryJournal = entryJournal;
        this.metrics = metrics;
        TransactionTemplate template = new TransactionTemplate(txManager);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        template.setName("ledger.transfer.naive");
        this.txTemplate = template;
    }

    @Override
    public TransferStrategyName name() {
        return TransferStrategyName.NAIVE;
    }

    @Override
    public TransferOutcome execute(TransferCommand command) {
        try {
            return metrics.timerFor(name()).recordCallable(() -> doTransfer(command));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private TransferOutcome doTransfer(TransferCommand cmd) {
        return txTemplate.execute(status -> {
            BigDecimal amount = cmd.amount().amount();

            BigDecimal fromBalance = readBalance(cmd.from());
            BigDecimal toBalance = readBalance(cmd.to());

            if (fromBalance.compareTo(amount) < 0) {
                metrics.insufficientFunds().increment();
                throw new InsufficientFundsException(cmd.from(), Money.of(fromBalance), cmd.amount());
            }

            BigDecimal newFromBalance = fromBalance.subtract(amount);
            BigDecimal newToBalance = toBalance.add(amount);

            jdbc.sql("UPDATE accounts SET balance = ? WHERE id = ?")
                    .params(newFromBalance, cmd.from().value())
                    .update();
            jdbc.sql("UPDATE accounts SET balance = ? WHERE id = ?")
                    .params(newToBalance, cmd.to().value())
                    .update();

            long entryId = entryJournal.appendEntry(cmd.from(), cmd.to(), cmd.amount());
            return new TransferOutcome(entryId, cmd.from(), cmd.to(), cmd.amount(), 1);
        });
    }

    private BigDecimal readBalance(AccountId id) {
        return jdbc.sql("SELECT balance FROM accounts WHERE id = ?")
                .param(id.value())
                .query(BigDecimal.class)
                .optional()
                .orElseThrow(() -> new AccountNotFoundException(id));
    }
}
