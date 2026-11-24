package com.bajaj.inventory_backend.sales.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Completed sale response DTO matching the approved POS API contract (POST /api/v1/sales).
 */
public record SaleResponse(
        Long id,
        String saleNumber,
        Long customerId,
        String customerName,
        Instant saleDate,
        String status,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal total,
        String paymentMethod,
        BigDecimal amountTendered,
        BigDecimal changeDue,
        String paymentReference,
        Long createdById,
        String createdByName,
        List<SaleItemResponse> items
) {
}

