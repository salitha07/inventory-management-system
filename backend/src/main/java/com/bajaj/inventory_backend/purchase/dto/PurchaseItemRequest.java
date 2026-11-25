package com.bajaj.inventory_backend.purchase.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

public record PurchaseItemRequest(

        @NotNull
        @Positive
        Long productId,

        @NotNull
        @Min(1)
        Integer quantity,

        @NotNull
        @DecimalMin("0.00")
        @Digits(integer = 10, fraction = 2)
        BigDecimal unitCost

) {}