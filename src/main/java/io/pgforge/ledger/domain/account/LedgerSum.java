package io.pgforge.ledger.domain.account;

public record LedgerSum(Money total, long accountCount, long entryCount) {}
