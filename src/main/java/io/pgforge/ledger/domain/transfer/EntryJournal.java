package io.pgforge.ledger.domain.transfer;

import io.pgforge.ledger.domain.account.AccountId;
import io.pgforge.ledger.domain.account.Money;

/**
 * Outbound port: appends an immutable entry that records a transfer.
 * Implementations join the caller's transaction.
 */
public interface EntryJournal {

    long appendEntry(AccountId from, AccountId to, Money amount);
}
