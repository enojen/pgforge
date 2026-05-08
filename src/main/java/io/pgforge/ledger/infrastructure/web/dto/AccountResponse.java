package io.pgforge.ledger.infrastructure.web.dto;

import java.math.BigDecimal;

import io.pgforge.ledger.domain.account.Account;

public record AccountResponse(long id, String name, BigDecimal balance, long version) {

    public static AccountResponse of(Account account) {
        return new AccountResponse(
                account.id().value(), account.name(), account.balance().amount(), account.version());
    }
}
