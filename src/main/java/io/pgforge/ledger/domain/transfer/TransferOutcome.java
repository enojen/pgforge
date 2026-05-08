package io.pgforge.ledger.domain.transfer;

import io.pgforge.ledger.domain.account.AccountId;
import io.pgforge.ledger.domain.account.Money;

public record TransferOutcome(long entryId, AccountId from, AccountId to, Money amount, int attempts) {

    public TransferOutcome {
        if (attempts < 1) {
            throw new IllegalArgumentException("attempts must be >= 1, was " + attempts);
        }
    }
}
