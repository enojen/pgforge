package io.pgforge.ledger.infrastructure.web.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateAccountRequestDto(
        @NotBlank @Size(max = 255) String name, @NotNull @PositiveOrZero BigDecimal initialBalance) {}
