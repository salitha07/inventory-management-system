package com.bajaj.inventory_backend.service;

import com.bajaj.inventory_backend.dto.user.*;
import com.bajaj.inventory_backend.entity.Role;
import com.bajaj.inventory_backend.entity.RoleName;
import com.bajaj.inventory_backend.entity.User;
import com.bajaj.inventory_backend.repository.RoleRepository;
import com.bajaj.inventory_backend.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {

        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // CREATE USER
    public UserResponse createUser(CreateUserRequest request) {

        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email address is already registered"
            );
        }

        Role role = findRole(request.role());

        User user = new User();

        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPasswordHash(
                passwordEncoder.encode(request.password())
        );
        user.setRole(role);
        user.setActive(true);

        return toResponse(userRepository.save(user));
    }

    // GET ALL USERS
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {

        return userRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // GET USER BY ID
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {

        return toResponse(findUser(id));
    }

    // UPDATE USER
    public UserResponse updateUser(
            Long id,
            UpdateUserRequest request) {

        User user = findUser(id);

        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmailAndIdNot(email, id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email address is already registered"
            );
        }

        Role newRole = findRole(request.role());

        // Prevent removing the last active administrator.
        if (user.getRole().getName() == RoleName.ADMIN
                && request.role() != RoleName.ADMIN
                && user.isActive()
                && userRepository.countByRole_NameAndActiveTrue(
                        RoleName.ADMIN) <= 1) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot change the role of the last active administrator"
            );
        }

        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setRole(newRole);

        // Change password only when supplied.
        if (request.password() != null
                && !request.password().isBlank()) {

            user.setPasswordHash(
                    passwordEncoder.encode(request.password())
            );
        }

        return toResponse(userRepository.save(user));
    }

    // ACTIVATE / DEACTIVATE USER
    public UserResponse updateUserStatus(
            Long id,
            UpdateUserStatusRequest request) {

        User user = findUser(id);

        boolean newStatus = request.active();

        if (!newStatus
                && user.getRole().getName() == RoleName.ADMIN
                && user.isActive()
                && userRepository.countByRole_NameAndActiveTrue(
                        RoleName.ADMIN) <= 1) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot deactivate the last active administrator"
            );
        }

        user.setActive(newStatus);

        return toResponse(userRepository.save(user));
    }

    // FIND USER
    private User findUser(Long id) {

        return userRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "User not found"
                        )
                );
    }

    // FIND ROLE
    private Role findRole(RoleName roleName) {

        return roleRepository.findByName(roleName)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Invalid user role"
                        )
                );
    }

    // NORMALIZE EMAIL
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    // CONVERT ENTITY TO RESPONSE
    private UserResponse toResponse(User user) {

        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().getName().name(),
                user.isActive(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}