package io.pgforge.ledger.domain.account;

public record AccountId(long value) {

    public AccountId {
        if (value <= 0) {
            throw new IllegalArgumentException("AccountId must be positive, was " + value);
        }
    }

    public static AccountId of(long value) {
        return new AccountId(value);
    }
}
