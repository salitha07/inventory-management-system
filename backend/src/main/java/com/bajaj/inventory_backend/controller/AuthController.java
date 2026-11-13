package com.bajaj.inventory_backend.controller;

import com.bajaj.inventory_backend.dto.auth.AuthResponse;
import com.bajaj.inventory_backend.dto.auth.AuthenticatedUserResponse;
import com.bajaj.inventory_backend.dto.auth.LoginRequest;
import com.bajaj.inventory_backend.service.AuthService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {

        AuthResponse response = authService.login(request);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<AuthenticatedUserResponse> getCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails) {

        AuthenticatedUserResponse response =
                authService.getCurrentUser(userDetails.getUsername());

        return ResponseEntity.ok(response);
    }
}