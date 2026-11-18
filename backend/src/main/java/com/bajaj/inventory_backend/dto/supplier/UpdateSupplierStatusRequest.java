package com.bajaj.inventory_backend.dto.supplier;

import jakarta.validation.constraints.NotNull;

public record UpdateSupplierStatusRequest(
        @NotNull(message = "Active status is required")
        Boolean active
) {}
