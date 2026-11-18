package com.bajaj.inventory_backend.sales.dto;

import java.math.BigDecimal;

/**
 * Line item response DTO representing a finalized sale line item.
 */
public record SaleItemResponse(
        Long id,
        Long productId,
        String partNumber,
        String productName,
        Integer quantity,
        BigDecimal unitSellingPrice,
        BigDecimal unitCostSnapshot,
        BigDecimal lineTotal
) {
}

