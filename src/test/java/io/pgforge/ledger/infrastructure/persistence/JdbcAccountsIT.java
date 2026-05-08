package io.pgforge.ledger.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import io.pgforge.TestcontainersConfig;
import io.pgforge.ledger.domain.account.Account;
import io.pgforge.ledger.domain.account.AccountId;
import io.pgforge.ledger.domain.account.LedgerSum;
import io.pgforge.ledger.domain.account.Money;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfig.class)
class JdbcAccountsIT {

    @Autowired
    private JdbcAccounts accounts;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void resetSchema() {
        jdbc.sql("TRUNCATE TABLE entries, accounts RESTART IDENTITY CASCADE").update();
    }

    @Test
    void create_persistsRowAndReturnsAccount() {
        Account a = accounts.create("alice", Money.of("250.00"));
        assertThat(a.id().value()).isPositive();
        assertThat(a.name()).isEqualTo("alice");
        assertThat(a.balance().amount()).isEqualByComparingTo("250.00");
        assertThat(a.version()).isZero();
    }

    @Test
    void findById_returnsExistingAccount() {
        Account inserted = accounts.create("bob", Money.of("10.00"));
        assertThat(accounts.findById(inserted.id())).isPresent().get().isEqualTo(inserted);
    }

    @Test
    void findById_returnsEmpty_whenAbsent() {
        assertThat(accounts.findById(AccountId.of(999_999))).isEmpty();
    }

    @Test
    void count_returnsAccountTableRowCount() {
        accounts.create("a", Money.of("0.00"));
        accounts.create("b", Money.of("0.00"));
        accounts.create("c", Money.of("0.00"));
        assertThat(accounts.count()).isEqualTo(3);
    }

    @Test
    void sum_aggregatesAcrossAllAccountsAndCountsEntries() {
        Account a = accounts.create("a", Money.of("100.00"));
        Account b = accounts.create("b", Money.of("250.50"));
        jdbc.sql("INSERT INTO entries (from_account_id, to_account_id, amount) VALUES (?, ?, ?)")
                .params(a.id().value(), b.id().value(), new java.math.BigDecimal("5.00"))
                .update();

        LedgerSum sum = accounts.sum();
        assertThat(sum.total().amount()).isEqualByComparingTo("350.50");
        assertThat(sum.accountCount()).isEqualTo(2);
        assertThat(sum.entryCount()).isEqualTo(1);
    }
}
