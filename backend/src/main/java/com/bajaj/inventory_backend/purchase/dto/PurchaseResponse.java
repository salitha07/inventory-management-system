package com.bajaj.inventory_backend.purchase.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PurchaseResponse(
        Long id,
        String purchaseNumber,
        Long supplierId,
        String supplierInvoiceNumber,
        LocalDate purchaseDate,
        String status,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal total,
        Long createdBy,
        List<PurchaseItemResponse> items
) {
}
