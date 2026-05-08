package io.pgforge.ledger.infrastructure.persistence.strategy;

import java.math.BigDecimal;

/** Row shape for {@code SELECT id, balance, version FROM accounts}. Package-private. */
record VersionedBalanceRow(long id, BigDecimal balance, long version) {}
