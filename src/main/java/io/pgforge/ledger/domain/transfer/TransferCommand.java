package io.pgforge.ledger.domain.transfer;

import java.util.Objects;

import io.pgforge.ledger.domain.account.AccountId;
import io.pgforge.ledger.domain.account.Money;

/**
 * Validated input for a transfer use case. Crash-early invariants:
 * non-null fields, distinct accounts, positive amount.
 */
public record TransferCommand(AccountId from, AccountId to, Money amount) {

    public TransferCommand {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(amount, "amount");
        if (from.equals(to)) {
            throw new IllegalArgumentException("from and to accounts must differ: " + from);
        }
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("amount must be positive, was " + amount.amount());
        }
    }
}
