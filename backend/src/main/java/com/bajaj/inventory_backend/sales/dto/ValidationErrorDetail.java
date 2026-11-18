package com.bajaj.inventory_backend.sales.dto;

/**
 * Encapsulates a single field validation violation detail.
 */
public record ValidationErrorDetail(
        String field,
        Object rejectedValue,
        String message
) {
}

