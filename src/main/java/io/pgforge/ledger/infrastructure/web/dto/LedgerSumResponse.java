package io.pgforge.ledger.infrastructure.web.dto;

import java.math.BigDecimal;

import io.pgforge.ledger.domain.account.LedgerSum;

public record LedgerSumResponse(BigDecimal sum, long accountCount, long entryCount) {

    public static LedgerSumResponse of(LedgerSum value) {
        return new LedgerSumResponse(value.total().amount(), value.accountCount(), value.entryCount());
    }
}
