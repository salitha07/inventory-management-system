package com.bajaj.inventory_backend.inventory.domain;

/**
 * Required Member 6 vocabulary. Provisional domain type, not the shared Sales enum.
 * Consolidation with inventory.entity.StockMovementType awaits team agreement.
 */
public enum MovementKind {
    PURCHASE_RECEIVE, SALE_DEDUCT, CUSTOMER_RETURN, MANUAL_ADJUSTMENT;

    public void validateChange(int change) {
        if (change == 0 || (this == SALE_DEDUCT && change > 0)
                || ((this == PURCHASE_RECEIVE || this == CUSTOMER_RETURN) && change < 0)) {
            throw new IllegalArgumentException("Quantity change contradicts movement kind");
        }
    }

    /** Pure arithmetic only; caller must supply a transactionally protected balance. */
    public int quantityAfter(int quantityBefore, int change) {
        if (quantityBefore < 0) {
            throw new IllegalArgumentException("Current quantity must be nonnegative");
        }
        validateChange(change);
        int after = Math.addExact(quantityBefore, change);
        if (after < 0) {
            throw new IllegalArgumentException("Resulting quantity must be nonnegative");
        }
        return after;
    }
}
