
package com.bajaj.inventory_backend.purchase.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreatePurchaseRequest(

        @NotNull
        Long supplierId,

        @NotBlank
        String supplierInvoiceNumber,

        @NotNull
        LocalDate purchaseDate,

        @NotNull
        @DecimalMin("0.00")
        BigDecimal discount,

        @NotEmpty
        List<@Valid PurchaseItemRequest> items

) {}
