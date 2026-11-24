package com.bajaj.inventory_backend.repository;

import com.bajaj.inventory_backend.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    List<Supplier> findAllByOrderByCreatedAtDesc();

    List<Supplier> findByActiveOrderByCreatedAtDesc(boolean active);

    boolean existsBySupplierNameIgnoreCase(String supplierName);

    boolean existsBySupplierNameIgnoreCaseAndIdNot(
            String supplierName, Long id);

    List<Supplier> findBySupplierNameContainingIgnoreCaseOrPhoneContainingOrEmailContainingIgnoreCaseOrderByCreatedAtDesc(
            String supplierName, String phone, String email);
}
