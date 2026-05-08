package io.pgforge.ledger.infrastructure.web.dto;

import java.math.BigDecimal;

import io.pgforge.ledger.domain.transfer.TransferOutcome;

public record TransferResponse(
        long entryId, long fromAccountId, long toAccountId, BigDecimal amount, int attempts) {

    public static TransferResponse of(TransferOutcome outcome) {
        return new TransferResponse(
                outcome.entryId(),
                outcome.from().value(),
                outcome.to().value(),
                outcome.amount().amount(),
                outcome.attempts());
    }
}
