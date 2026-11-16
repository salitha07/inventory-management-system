package com.bajaj.inventory_backend.dto.user;

import com.bajaj.inventory_backend.entity.RoleName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(

        @NotBlank(message = "Full name is required")
        @Size(max = 120)
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email address")
        @Size(max = 150)
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72,
                message = "Password must contain 8 to 72 characters")
        String password,

        @NotNull(message = "Role is required")
        RoleName role

) {}