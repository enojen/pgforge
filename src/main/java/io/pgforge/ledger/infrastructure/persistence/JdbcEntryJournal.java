package io.pgforge.ledger.infrastructure.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.pgforge.ledger.domain.account.AccountId;
import io.pgforge.ledger.domain.account.Money;
import io.pgforge.ledger.domain.transfer.EntryJournal;

@Component
public class JdbcEntryJournal implements EntryJournal {

    private final JdbcClient jdbc;

    public JdbcEntryJournal(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long appendEntry(AccountId from, AccountId to, Money amount) {
        return jdbc.sql("INSERT INTO entries (from_account_id, to_account_id, amount) "
                        + "VALUES (?, ?, ?) RETURNING id")
                .params(from.value(), to.value(), amount.amount())
                .query(Long.class)
                .single();
    }
}
