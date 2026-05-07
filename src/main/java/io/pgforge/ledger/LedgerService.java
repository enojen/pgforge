package io.pgforge.ledger;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class LedgerService {

    private final JdbcClient jdbcClient;

    public LedgerService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public long accountCount() {
        return jdbcClient
                .sql("SELECT count(*) FROM accounts")
                .query(Long.class)
                .single();
    }
}
