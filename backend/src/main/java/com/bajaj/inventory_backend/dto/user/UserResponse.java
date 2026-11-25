package com.bajaj.inventory_backend.dto.user;

import java.time.Instant;

public record UserResponse(

        Long id,
        String fullName,
        String email,
        String role,
        boolean active,
        Instant createdAt,
        Instant updatedAt

) {}