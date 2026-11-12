package com.bajaj.inventory_backend.repository;

import com.bajaj.inventory_backend.entity.Role;
import com.bajaj.inventory_backend.entity.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(RoleName name);
}