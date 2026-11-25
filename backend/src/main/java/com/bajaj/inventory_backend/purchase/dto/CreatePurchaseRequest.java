package com.bajaj.inventory_backend.purchase.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreatePurchaseRequest(

        @NotNull
        @Positive
        Long supplierId,

        @NotBlank
        @Size(max = 100)
        String supplierInvoiceNumber,

        @NotNull
        LocalDate purchaseDate,

        @NotNull
        @DecimalMin("0.00")
        @Digits(integer = 10, fraction = 2)
        BigDecimal discount,

        @NotEmpty
        List<@NotNull @Valid PurchaseItemRequest> items

) {}