package io.pgforge.ledger.domain.account;

public class AccountNotFoundException extends RuntimeException {

    private final AccountId accountId;

    public AccountNotFoundException(AccountId accountId) {
        super("account %d not found".formatted(accountId.value()));
        this.accountId = accountId;
    }

    public AccountId accountId() {
        return accountId;
    }
}
