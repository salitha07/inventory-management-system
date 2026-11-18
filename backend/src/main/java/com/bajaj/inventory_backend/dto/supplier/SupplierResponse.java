package com.bajaj.inventory_backend.dto.supplier;

import java.time.Instant;

public record SupplierResponse(
        Long id,
        String supplierName,
        String contactPerson,
        String phone,
        String email,
        String address,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
