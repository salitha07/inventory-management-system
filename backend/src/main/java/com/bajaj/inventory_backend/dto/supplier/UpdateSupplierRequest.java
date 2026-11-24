package com.bajaj.inventory_backend.dto.supplier;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateSupplierRequest(

        @NotBlank(message = "Supplier name is required")
        @Size(max = 150)
        String supplierName,

        @Size(max = 120)
        String contactPerson,

        @NotBlank(message = "Phone number is required")
        @Size(max = 20)
        String phone,

        @Email(message = "Invalid email address")
        @Size(max = 150)
        String email,

        @Size(max = 500)
        String address

) {}
