package com.bajaj.inventory_backend.config;

import com.bajaj.inventory_backend.entity.Role;
import com.bajaj.inventory_backend.entity.RoleName;
import com.bajaj.inventory_backend.entity.User;
import com.bajaj.inventory_backend.repository.RoleRepository;
import com.bajaj.inventory_backend.repository.UserRepository;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrapRunner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapRunner(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {

        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {

        String email = System.getenv("INITIAL_ADMIN_EMAIL");
        String fullName = System.getenv("INITIAL_ADMIN_NAME");
        String password = System.getenv("INITIAL_ADMIN_PASSWORD");

        // Do not create an account without all required credentials.
        if (email == null || email.isBlank()
                || fullName == null || fullName.isBlank()
                || password == null || password.isBlank()) {

            System.out.println(
                    "Initial ADMIN creation skipped: credentials not configured."
            );
            return;
        }

        // Only create the initial administrator once.
        if (userRepository.existsByRole_Name(RoleName.ADMIN)) {
            System.out.println("An ADMIN account already exists. Skipping bootstrap.");
            return;
        }

        if (userRepository.existsByEmail(email)) {
            System.out.println("The specified admin email already exists. Skipping bootstrap.");
            return;
        }

        Role adminRole = roleRepository.findByName(RoleName.ADMIN)
                .orElseThrow(() ->
                        new IllegalStateException("ADMIN role not found in database.")
                );

        User admin = new User();

        admin.setFullName(fullName);
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(adminRole);
        admin.setActive(true);

        userRepository.save(admin);

        System.out.println("Initial ADMIN account created successfully.");
    }
}