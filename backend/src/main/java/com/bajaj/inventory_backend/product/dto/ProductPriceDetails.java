package com.bajaj.inventory_backend.product.dto;

import java.math.BigDecimal;

/**
 * Authoritative pricing and master metadata for a spare part in the active catalog.
 * Owned by Member 2 (Products & Catalog).
 */
public record ProductPriceDetails(
        Long productId,
        String partNumber,
        String name,
        BigDecimal sellingPrice,
        boolean active
) {
}

