package com.bajaj.inventory_backend.repository;

import com.bajaj.inventory_backend.entity.RoleName;
import com.bajaj.inventory_backend.entity.User;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = "role")
    Optional<User> findByEmail(String email);

    @EntityGraph(attributePaths = "role")
    Optional<User> findById(Long id);

    @EntityGraph(attributePaths = "role")
    List<User> findAllByOrderByCreatedAtDesc();

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    boolean existsByRole_Name(RoleName roleName);

    long countByRole_NameAndActiveTrue(RoleName roleName);
}