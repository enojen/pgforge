package io.pgforge.ledger.application.account;

import java.util.Objects;

import io.pgforge.ledger.domain.account.Account;
import io.pgforge.ledger.domain.account.AccountId;
import io.pgforge.ledger.domain.account.AccountNotFoundException;
import io.pgforge.ledger.domain.account.Accounts;
import io.pgforge.ledger.domain.account.LedgerSum;
import io.pgforge.ledger.domain.account.Money;

/**
 * Account use cases. Pure POJO — Spring assembles it via LedgerBeansConfig.
 */
public class AccountService {

    private final Accounts accounts;

    public AccountService(Accounts accounts) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
    }

    public Account create(String name, Money initialBalance) {
        return accounts.create(name, initialBalance);
    }

    public Account get(AccountId id) {
        return accounts.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    public long count() {
        return accounts.count();
    }

    public LedgerSum sum() {
        return accounts.sum();
    }
}
