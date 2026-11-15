package com.bajaj.inventory_backend.sales.validation;

import com.bajaj.inventory_backend.sales.PaymentMethod;
import com.bajaj.inventory_backend.sales.dto.SaleCreateRequest;
import com.bajaj.inventory_backend.sales.dto.SaleItemRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaleCreateRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Valid SaleCreateRequest passes bean validation")
    void testValidRequest_Passes() {
        SaleItemRequest item = new SaleItemRequest(101L, 2);
        SaleCreateRequest request = new SaleCreateRequest(
                null,
                PaymentMethod.CASH,
                new BigDecimal("500.00"),
                null,
                BigDecimal.ZERO,
                List.of(item)
        );

        Set<ConstraintViolation<SaleCreateRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Should have no validation violations");
    }

    @Test
    @DisplayName("Empty items list violates @NotEmpty")
    void testEmptyItems_Violates() {
        SaleCreateRequest request = new SaleCreateRequest(
                null,
                PaymentMethod.CASH,
                new BigDecimal("500.00"),
                null,
                BigDecimal.ZERO,
                Collections.emptyList()
        );

        Set<ConstraintViolation<SaleCreateRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("items")));
    }

    @Test
    @DisplayName("Negative discount violates @DecimalMin")
    void testNegativeDiscount_Violates() {
        SaleItemRequest item = new SaleItemRequest(101L, 1);
        SaleCreateRequest request = new SaleCreateRequest(
                null,
                PaymentMethod.CASH,
                new BigDecimal("500.00"),
                null,
                new BigDecimal("-10.00"),
                List.of(item)
        );

        Set<ConstraintViolation<SaleCreateRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("discount")));
    }

    @Test
    @DisplayName("Item with 0 quantity violates @Min(1)")
    void testZeroQuantityItem_Violates() {
        SaleItemRequest item = new SaleItemRequest(101L, 0);
        SaleCreateRequest request = new SaleCreateRequest(
                null,
                PaymentMethod.CASH,
                new BigDecimal("500.00"),
                null,
                BigDecimal.ZERO,
                List.of(item)
        );

        Set<ConstraintViolation<SaleCreateRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Quantity must be at least 1")));
    }
}

