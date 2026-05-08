package io.pgforge.ledger.infrastructure.persistence;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.pgforge.ledger.domain.account.Account;
import io.pgforge.ledger.domain.account.AccountId;
import io.pgforge.ledger.domain.account.Accounts;
import io.pgforge.ledger.domain.account.LedgerSum;
import io.pgforge.ledger.domain.account.Money;

@Repository
public class JdbcAccounts implements Accounts {

    private final JdbcClient jdbc;

    public JdbcAccounts(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Account create(String name, Money initialBalance) {
        return jdbc.sql("INSERT INTO accounts (name, balance) VALUES (?, ?) "
                        + "RETURNING id, name, balance, version")
                .params(name, initialBalance.amount())
                .query((rs, rowNum) -> mapAccount(rs))
                .single();
    }

    @Override
    public Optional<Account> findById(AccountId id) {
        return jdbc.sql("SELECT id, name, balance, version FROM accounts WHERE id = ?")
                .param(id.value())
                .query((rs, rowNum) -> mapAccount(rs))
                .optional();
    }

    @Override
    public long count() {
        return jdbc.sql("SELECT count(*) FROM accounts").query(Long.class).single();
    }

    @Override
    public LedgerSum sum() {
        return jdbc.sql("SELECT COALESCE(SUM(balance), 0) AS total, "
                        + "(SELECT count(*) FROM accounts) AS account_count, "
                        + "(SELECT count(*) FROM entries) AS entry_count "
                        + "FROM accounts")
                .query((rs, rowNum) -> new LedgerSum(
                        Money.of(rs.getBigDecimal("total")),
                        rs.getLong("account_count"),
                        rs.getLong("entry_count")))
                .single();
    }

    private static Account mapAccount(java.sql.ResultSet rs) throws java.sql.SQLException {
        AccountId id = AccountId.of(rs.getLong("id"));
        String name = rs.getString("name");
        BigDecimal balance = rs.getBigDecimal("balance");
        long version = rs.getLong("version");
        return new Account(id, name, Money.of(balance), version);
    }
}
