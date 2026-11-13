package com.bajaj.inventory_backend.dto.auth;

public record AuthenticatedUserResponse(
        Long id,
        String fullName,
        String email,
        String role,
        boolean active
) {
}