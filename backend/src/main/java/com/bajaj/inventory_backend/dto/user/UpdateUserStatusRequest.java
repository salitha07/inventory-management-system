package com.bajaj.inventory_backend.dto.user;

import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(

        @NotNull(message = "Active status is required")
        Boolean active

) {}