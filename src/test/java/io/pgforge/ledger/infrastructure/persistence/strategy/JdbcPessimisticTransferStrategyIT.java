package io.pgforge.ledger.infrastructure.persistence.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

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
import io.pgforge.ledger.domain.transfer.TransferCommand;
import io.pgforge.ledger.domain.transfer.TransferOutcome;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfig.class)
class JdbcPessimisticTransferStrategyIT {

    @Autowired
    private JdbcPessimisticTransferStrategy strategy;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void resetSchema() {
        jdbc.sql("TRUNCATE TABLE entries, accounts RESTART IDENTITY CASCADE").update();
    }

    @Test
    void transfer_movesMoneyBetweenAccounts() {
        long from = insertAccount("alice", "100.00");
        long to = insertAccount("bob", "20.00");

        TransferOutcome outcome = strategy.execute(
                new TransferCommand(AccountId.of(from), AccountId.of(to), Money.of("25.00")));

        assertThat(outcome.attempts()).isEqualTo(1);
        assertThat(balance(from)).isEqualByComparingTo("75.00");
        assertThat(balance(to)).isEqualByComparingTo("45.00");
    }

    @Test
    void transfer_throwsInsufficientFunds() {
        long from = insertAccount("low", "5.00");
        long to = insertAccount("any", "0.00");

        assertThatThrownBy(() -> strategy.execute(
                        new TransferCommand(AccountId.of(from), AccountId.of(to), Money.of("100.00"))))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void transfer_doesNotChangeVersionColumn() {
        long from = insertAccount("a", "10.00");
        long to = insertAccount("b", "0.00");
        long fromVersionBefore = version(from);

        strategy.execute(new TransferCommand(AccountId.of(from), AccountId.of(to), Money.of("1.00")));

        assertThat(version(from)).isEqualTo(fromVersionBefore);
        assertThat(version(to)).isEqualTo(fromVersionBefore);
    }

    private long insertAccount(String name, String balance) {
        return jdbc.sql("INSERT INTO accounts (name, balance) VALUES (?, ?) RETURNING id")
                .params(name, new BigDecimal(balance))
                .query(Long.class)
                .single();
    }

    private BigDecimal balance(long id) {
        return jdbc.sql("SELECT balance FROM accounts WHERE id = ?")
                .param(id)
                .query(BigDecimal.class)
                .single();
    }

    private long version(long id) {
        return jdbc.sql("SELECT version FROM accounts WHERE id = ?")
                .param(id)
                .query(Long.class)
                .single();
    }
}
