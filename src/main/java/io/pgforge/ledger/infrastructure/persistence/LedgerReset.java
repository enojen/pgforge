package io.pgforge.ledger.infrastructure.persistence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Profile-gated admin operation: truncate ledger tables and reset id sequences.
 * Bean is only registered when {@code pgforge.admin.enabled=true}.
 */
@Component
@ConditionalOnProperty(prefix = "pgforge.admin", name = "enabled", havingValue = "true")
public class LedgerReset {

    private final JdbcClient jdbc;

    public LedgerReset(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void resetLedger() {
        jdbc.sql("TRUNCATE TABLE entries, accounts RESTART IDENTITY CASCADE").update();
    }
}
