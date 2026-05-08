package io.pgforge.ledger.domain.account;

import java.util.Objects;

/**
 * Immutable account aggregate. Invariants enforced in constructor and mutators.
 * Mutators ({@link #debit(Money)}, {@link #credit(Money)}) return new instances
 * — domain stays free of side effects.
 */
public record Account(AccountId id, String name, Money balance, long version) {

    public Account {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(balance, "balance");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must be non-negative, was " + version);
        }
    }

    public Account debit(Money amount) {
        if (balance.isLessThan(amount)) {
            throw new InsufficientFundsException(id, balance, amount);
        }
        return new Account(id, name, balance.subtract(amount), version + 1);
    }

    public Account credit(Money amount) {
        return new Account(id, name, balance.add(amount), version + 1);
    }
}
