package io.pgforge.ledger.infrastructure.persistence.strategy;

import java.math.BigDecimal;

/** Row shape for {@code SELECT id, balance FROM accounts}. Package-private. */
record BalanceRow(long id, BigDecimal balance) {}
