package io.pgforge.ledger.infrastructure.web.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TransferRequestDto(
        @NotNull @Positive Long fromAccountId,
        @NotNull @Positive Long toAccountId,
        @NotNull @DecimalMin(value = "0.01", inclusive = true) BigDecimal amount) {

    @AssertTrue(message = "fromAccountId and toAccountId must differ")
    public boolean isDifferentAccounts() {
        return fromAccountId == null || toAccountId == null || !fromAccountId.equals(toAccountId);
    }
}
