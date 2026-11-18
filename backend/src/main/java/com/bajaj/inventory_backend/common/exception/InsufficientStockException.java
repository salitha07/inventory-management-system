package com.bajaj.inventory_backend.common.exception;

/**
 * Thrown when an inventory stock check fails due to insufficient on-hand inventory.
 * Triggers HTTP 409 Conflict and Spring transaction rollback.
 */
public class InsufficientStockException extends RuntimeException {

    private final Long productId;
    private final String productName;
    private final Integer requestedQuantity;
    private final Integer availableQuantity;

    public InsufficientStockException(Long productId, String productName,
                                     Integer requestedQuantity, Integer availableQuantity) {
        super(String.format(
                "Insufficient stock for product ID %d ('%s'). Requested: %d, Available: %d.",
                productId,
                productName != null ? productName : "Unknown Part",
                requestedQuantity,
                availableQuantity != null ? availableQuantity : 0
        ));
        this.productId = productId;
        this.productName = productName;
        this.requestedQuantity = requestedQuantity;
        this.availableQuantity = availableQuantity;
    }

    public InsufficientStockException(Long productId, Integer requestedQuantity, Integer availableQuantity) {
        this(productId, "Part #" + productId, requestedQuantity, availableQuantity);
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public Integer getRequestedQuantity() {
        return requestedQuantity;
    }

    public Integer getAvailableQuantity() {
        return availableQuantity;
    }
}

