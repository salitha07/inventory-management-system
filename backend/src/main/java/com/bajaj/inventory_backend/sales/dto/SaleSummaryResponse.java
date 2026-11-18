package com.bajaj.inventory_backend.sales.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Summary projection DTO for paginated sales history lists (GET /api/v1/sales).
 */
public record SaleSummaryResponse(
        Long id,
        String saleNumber,
        Long customerId,
        String customerName,
        Instant saleDate,
        String status,
        Integer itemCount,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal total,
        String paymentMethod,
        Long createdById,
        String createdByName
) {
}

