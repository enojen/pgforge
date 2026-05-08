package io.pgforge.ledger.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void allowsZero() {
        assertThat(Money.zero().amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void addsAndSubtracts() {
        Money a = Money.of("100.50");
        Money b = Money.of("50.25");
        assertThat(a.add(b).amount()).isEqualByComparingTo("150.75");
        assertThat(a.subtract(b).amount()).isEqualByComparingTo("50.25");
    }

    @Test
    void subtractRefusesGoingNegative() {
        assertThatThrownBy(() -> Money.of("10.00").subtract(Money.of("11.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void comparesByAmount() {
        assertThat(Money.of("9.99").isLessThan(Money.of("10.00"))).isTrue();
        assertThat(Money.of("10.00").isLessThan(Money.of("9.99"))).isFalse();
        assertThat(Money.of("10.00").isLessThan(Money.of("10.00"))).isFalse();
    }

    @Test
    void isPositiveTracksSignum() {
        assertThat(Money.zero().isPositive()).isFalse();
        assertThat(Money.of("0.01").isPositive()).isTrue();
    }
}
