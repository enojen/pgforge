package io.pgforge.ledger.infrastructure.config;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.pgforge.ledger.application.account.AccountService;
import io.pgforge.ledger.application.transfer.TransferService;
import io.pgforge.ledger.application.transfer.retry.RetryPolicy;
import io.pgforge.ledger.domain.account.Accounts;
import io.pgforge.ledger.domain.transfer.TransferStrategy;
import io.pgforge.ledger.domain.transfer.TransferStrategyName;
import io.pgforge.ledger.infrastructure.persistence.strategy.JdbcNaiveTransferStrategy;
import io.pgforge.ledger.infrastructure.persistence.strategy.JdbcOptimisticTransferStrategy;
import io.pgforge.ledger.infrastructure.persistence.strategy.JdbcPessimisticTransferStrategy;

/**
 * Manual wiring of the pure-POJO application use cases.
 *
 * <p>The {@code application} layer has no Spring annotations by design — assembly is a
 * concern that lives only in {@code infrastructure}. This is the single seam where
 * domain ports, application services, and infrastructure adapters meet.
 */
@Configuration
public class LedgerBeansConfig {

    @Bean
    public RetryPolicy optimisticRetryPolicy() {
        return RetryPolicy.defaults();
    }

    @Bean
    public AccountService accountService(Accounts accounts) {
        return new AccountService(accounts);
    }

    @Bean
    public TransferService transferService(
            JdbcNaiveTransferStrategy naive,
            JdbcPessimisticTransferStrategy pessimistic,
            JdbcOptimisticTransferStrategy optimistic) {
        Map<TransferStrategyName, TransferStrategy> strategies = Map.of(
                TransferStrategyName.NAIVE, naive,
                TransferStrategyName.PESSIMISTIC, pessimistic,
                TransferStrategyName.OPTIMISTIC, optimistic);
        return new TransferService(strategies);
    }
}
