package io.pgforge.ledger.domain.account;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Non-negative monetary amount with two-decimal precision.
 * Crash-early: rejects negative or null values at construction.
 */
public record Money(BigDecimal amount) implements Comparable<Money> {

    private static final int SCALE = 2;

    public Money {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Money cannot be negative, was " + amount);
        }
        amount = amount.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    public static Money of(String amount) {
        return new Money(new BigDecimal(amount));
    }

    public static Money of(long units) {
        return new Money(BigDecimal.valueOf(units));
    }

    public static Money zero() {
        return new Money(BigDecimal.ZERO);
    }

    public Money add(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money subtract(Money other) {
        BigDecimal result = amount.subtract(other.amount);
        if (result.signum() < 0) {
            throw new IllegalArgumentException(
                    "Money subtraction would go negative: " + amount + " - " + other.amount);
        }
        return new Money(result);
    }

    public boolean isLessThan(Money other) {
        return amount.compareTo(other.amount) < 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    @Override
    public int compareTo(Money other) {
        return amount.compareTo(other.amount);
    }
}
