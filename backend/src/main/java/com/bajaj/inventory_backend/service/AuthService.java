package com.bajaj.inventory_backend.service;

import com.bajaj.inventory_backend.dto.auth.AuthResponse;
import com.bajaj.inventory_backend.dto.auth.AuthenticatedUserResponse;
import com.bajaj.inventory_backend.dto.auth.LoginRequest;
import com.bajaj.inventory_backend.entity.User;
import com.bajaj.inventory_backend.repository.UserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Value("${app.jwt.expiration}")
    private long jwtExpiration;

    public AuthService(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            JwtService jwtService) {

        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {

        // Verify email and password
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.email(),
                        request.password()
                )
        );

        // Retrieve authenticated user
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found"));

        // Generate JWT
        String token = jwtService.generateToken(user.getEmail());

        // Prepare user information
        AuthenticatedUserResponse userResponse =
                new AuthenticatedUserResponse(
                        user.getId(),
                        user.getFullName(),
                        user.getEmail(),
                        user.getRole().getName().name(),
                        user.isActive()
                );

        // Return login response
        return new AuthResponse(
                token,
                "Bearer",
                jwtExpiration,
                userResponse
        );
    }
}