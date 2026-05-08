package io.pgforge.ledger.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import io.pgforge.TestcontainersConfig;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfig.class)
class LedgerSchemaIT {

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void canInsertAccount() {
        Long id = jdbcClient
                .sql("INSERT INTO accounts (name, balance) VALUES (?, ?) RETURNING id")
                .params("alice-schema", new BigDecimal("100.00"))
                .query(Long.class)
                .single();

        assertThat(id).isNotNull();

        BigDecimal balance = jdbcClient
                .sql("SELECT balance FROM accounts WHERE id = ?")
                .param(id)
                .query(BigDecimal.class)
                .single();

        assertThat(balance).isEqualByComparingTo("100.00");
    }

    @Test
    void entryRequiresPositiveAmount() {
        Long alice = insertAccount("alice-neg");
        Long bob = insertAccount("bob-neg");

        assertThatThrownBy(() -> jdbcClient
                        .sql("INSERT INTO entries (from_account_id, to_account_id, amount) VALUES (?, ?, ?)")
                        .params(alice, bob, new BigDecimal("0.00"))
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void entryRequiresExistingAccountFk() {
        Long alice = insertAccount("alice-fk");

        assertThatThrownBy(() -> jdbcClient
                        .sql("INSERT INTO entries (from_account_id, to_account_id, amount) VALUES (?, ?, ?)")
                        .params(alice, 999_999L, new BigDecimal("10.00"))
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Long insertAccount(String name) {
        return jdbcClient
                .sql("INSERT INTO accounts (name) VALUES (?) RETURNING id")
                .param(name)
                .query(Long.class)
                .single();
    }
}
