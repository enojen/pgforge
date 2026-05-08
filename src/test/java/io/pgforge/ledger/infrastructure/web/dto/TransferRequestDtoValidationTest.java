package io.pgforge.ledger.infrastructure.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class TransferRequestDtoValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void initValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeFactory() {
        factory.close();
    }

    @Test
    void validates_typicalRequest() {
        TransferRequestDto dto = new TransferRequestDto(1L, 2L, new BigDecimal("10.00"));
        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void rejectsSameAccountTransfer() {
        TransferRequestDto dto = new TransferRequestDto(1L, 1L, new BigDecimal("10.00"));
        Set<ConstraintViolation<TransferRequestDto>> violations = validator.validate(dto);
        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .anyMatch(m -> m.contains("differ"));
    }

    @Test
    void rejectsZeroAmount() {
        TransferRequestDto dto = new TransferRequestDto(1L, 2L, BigDecimal.ZERO);
        assertThat(validator.validate(dto)).isNotEmpty();
    }

    @Test
    void rejectsNullAccountId() {
        TransferRequestDto dto = new TransferRequestDto(null, 2L, new BigDecimal("10.00"));
        assertThat(validator.validate(dto)).isNotEmpty();
    }
}
