package io.pgforge.ledger.domain.account;

import java.util.Optional;

/**
 * Outbound port for account persistence. Implementations live in infrastructure.
 */
public interface Accounts {

    Account create(String name, Money initialBalance);

    Optional<Account> findById(AccountId id);

    long count();

    LedgerSum sum();
}
