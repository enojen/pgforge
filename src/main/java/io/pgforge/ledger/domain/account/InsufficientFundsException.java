package io.pgforge.ledger.domain.account;

public class InsufficientFundsException extends RuntimeException {

    private final AccountId accountId;
    private final Money balance;
    private final Money requested;

    public InsufficientFundsException(AccountId accountId, Money balance, Money requested) {
        super("account %d has balance %s, cannot debit %s"
                .formatted(accountId.value(), balance.amount(), requested.amount()));
        this.accountId = accountId;
        this.balance = balance;
        this.requested = requested;
    }

    public AccountId accountId() {
        return accountId;
    }

    public Money balance() {
        return balance;
    }

    public Money requested() {
        return requested;
    }
}
