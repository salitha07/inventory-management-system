package com.bajaj.inventory_backend.inventory.entity;

/**
 * Audit classification of stock ledger movements.
 */
public enum StockMovementType {
    SALE,
    RETURN,
    ADJUSTMENT,
    PURCHASE
}

