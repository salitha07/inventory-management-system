package com.bajaj.inventory_backend.sales.exception;

/**
 * Exception thrown when a checkout request violates business rules or validation invariants.
 */
public class InvalidCheckoutException extends RuntimeException {

    public InvalidCheckoutException(String message) {
        super(message);
    }
}

