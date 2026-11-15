package com.bajaj.inventory_backend.sales.dto;

import java.time.Instant;
import java.util.List;

/**
 * Standard error response DTO matching RFC 7807 conventions.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<ValidationErrorDetail> validationErrors
) {
    public ErrorResponse(Instant timestamp, int status, String error, String message, String path) {
        this(timestamp, status, error, message, path, null);
    }
}

