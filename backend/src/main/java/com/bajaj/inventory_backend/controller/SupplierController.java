package com.bajaj.inventory_backend.controller;

import com.bajaj.inventory_backend.dto.supplier.CreateSupplierRequest;
import com.bajaj.inventory_backend.dto.supplier.SupplierResponse;
import com.bajaj.inventory_backend.dto.supplier.UpdateSupplierRequest;
import com.bajaj.inventory_backend.dto.supplier.UpdateSupplierStatusRequest;
import com.bajaj.inventory_backend.service.SupplierService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @PostMapping
    public ResponseEntity<SupplierResponse> createSupplier(
            @Valid @RequestBody CreateSupplierRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(supplierService.createSupplier(request));
    }

    @GetMapping
    public ResponseEntity<List<SupplierResponse>> getAllSuppliers(
            @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(supplierService.getAllSuppliers(active));
    }


    @GetMapping("/search")
    public ResponseEntity<List<SupplierResponse>> searchSuppliers(
            @RequestParam String keyword) {
        return ResponseEntity.ok(
                supplierService.searchSuppliers(keyword));
    }
    @GetMapping("/{id}")
    public ResponseEntity<SupplierResponse> getSupplierById(
            @PathVariable Long id) {
        return ResponseEntity.ok(supplierService.getSupplierById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SupplierResponse> updateSupplier(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSupplierRequest request) {
        return ResponseEntity.ok(
                supplierService.updateSupplier(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<SupplierResponse> updateSupplierStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSupplierStatusRequest request) {
        return ResponseEntity.ok(
                supplierService.updateSupplierStatus(id, request));
    }
}
