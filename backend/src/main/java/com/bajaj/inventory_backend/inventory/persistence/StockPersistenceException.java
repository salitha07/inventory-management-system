package com.bajaj.inventory_backend.inventory.persistence;

/** Inventory-local persistence outcomes; shared API exception mapping remains pending. */
public final class StockPersistenceException extends IllegalStateException {
    public enum Reason { PRODUCT_NOT_FOUND, STOCK_NOT_INITIALIZED, INVALID_WRITE_PLAN, UNEXPECTED_ROW_COUNT, READ_ONLY }
    private final Reason reason;

    public StockPersistenceException(Reason reason) {
        super("Inventory persistence failure: " + reason);
        this.reason = reason;
    }

    public Reason reason() { return reason; }
}
