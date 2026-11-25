package com.bajaj.inventory_backend.common.exception;

/**
 * Thrown when a product ID does not exist in the active catalog or has been discontinued.
 */
public class ProductNotFoundException extends RuntimeException {

    private final Long productId;

    public ProductNotFoundException(Long productId) {
        super(String.format("Product with ID %d was not found or is discontinued in the active parts catalog.", productId));
        this.productId = productId;
    }

    public ProductNotFoundException(String message) {
        super(message);
        this.productId = null;
    }

    public ProductNotFoundException(Long productId, String message) {
        super(message);
        this.productId = productId;
    }

    public Long getProductId() {
        return productId;
    }
}

