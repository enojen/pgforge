package io.pgforge.ledger.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AccountTest {

    private final AccountId id = AccountId.of(1);

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> new Account(id, "  ", Money.of("0.00"), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void rejectsNegativeVersion() {
        assertThatThrownBy(() -> new Account(id, "alice", Money.of("0.00"), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void debit_throwsInsufficientFunds_whenBalanceTooLow() {
        Account a = new Account(id, "alice", Money.of("10.00"), 0);
        assertThatThrownBy(() -> a.debit(Money.of("10.01")))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void debit_returnsNewAccountWithReducedBalanceAndBumpedVersion() {
        Account a = new Account(id, "alice", Money.of("100.00"), 5);
        Account b = a.debit(Money.of("30.00"));
        assertThat(b.balance().amount()).isEqualByComparingTo("70.00");
        assertThat(b.version()).isEqualTo(6);
        assertThat(b).isNotSameAs(a);
    }

    @Test
    void credit_returnsNewAccountWithIncreasedBalanceAndBumpedVersion() {
        Account a = new Account(id, "alice", Money.of("100.00"), 5);
        Account b = a.credit(Money.of("30.00"));
        assertThat(b.balance().amount()).isEqualByComparingTo("130.00");
        assertThat(b.version()).isEqualTo(6);
    }
}
