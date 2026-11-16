
package com.bajaj.inventory_backend.purchase.dto;

import java.math.BigDecimal;

public record PurchaseItemResponse(
        Long id,
        Long productId,
        Integer quantity,
        BigDecimal unitCost,
        BigDecimal lineTotal
) {
}
