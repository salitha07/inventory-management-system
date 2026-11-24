package com.bajaj.inventory_backend.service;

import com.bajaj.inventory_backend.dto.supplier.CreateSupplierRequest;
import com.bajaj.inventory_backend.dto.supplier.SupplierResponse;
import com.bajaj.inventory_backend.dto.supplier.UpdateSupplierRequest;
import com.bajaj.inventory_backend.dto.supplier.UpdateSupplierStatusRequest;
import com.bajaj.inventory_backend.entity.Supplier;
import com.bajaj.inventory_backend.repository.SupplierRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional
public class SupplierService {

    private final SupplierRepository supplierRepository;

    public SupplierService(SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    public SupplierResponse createSupplier(CreateSupplierRequest request) {
        String supplierName = request.supplierName().trim();

        if (supplierRepository.existsBySupplierNameIgnoreCase(supplierName)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Supplier name already exists");
        }

        Supplier supplier = new Supplier();
        supplier.setSupplierName(supplierName);
        supplier.setContactPerson(normalize(request.contactPerson()));
        supplier.setPhone(request.phone().trim());
        supplier.setEmail(normalizeEmail(request.email()));
        supplier.setAddress(normalize(request.address()));

        return toResponse(supplierRepository.save(supplier));
    }

    @Transactional(readOnly = true)
    public List<SupplierResponse> getAllSuppliers(Boolean active) {
        List<Supplier> suppliers = active == null
                ? supplierRepository.findAllByOrderByCreatedAtDesc()
                : supplierRepository.findByActiveOrderByCreatedAtDesc(active);

        return suppliers.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SupplierResponse getSupplierById(Long id) {
        return toResponse(findSupplier(id));
    }

    public SupplierResponse updateSupplier(
            Long id, UpdateSupplierRequest request) {

        Supplier supplier = findSupplier(id);
        String supplierName = request.supplierName().trim();

        if (supplierRepository.existsBySupplierNameIgnoreCaseAndIdNot(
                supplierName, id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Supplier name already exists");
        }

        supplier.setSupplierName(supplierName);
        supplier.setContactPerson(normalize(request.contactPerson()));
        supplier.setPhone(request.phone().trim());
        supplier.setEmail(normalizeEmail(request.email()));
        supplier.setAddress(normalize(request.address()));

        return toResponse(supplierRepository.save(supplier));
    }

    public SupplierResponse updateSupplierStatus(
            Long id, UpdateSupplierStatusRequest request) {

        Supplier supplier = findSupplier(id);
        supplier.setActive(request.active());

        return toResponse(supplierRepository.save(supplier));
    }


    @Transactional(readOnly = true)
    public List<SupplierResponse> searchSuppliers(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return getAllSuppliers(null);
        }

        String searchTerm = keyword.trim();

        return supplierRepository
                .findBySupplierNameContainingIgnoreCaseOrPhoneContainingOrEmailContainingIgnoreCaseOrderByCreatedAtDesc(
                        searchTerm, searchTerm, searchTerm)
                .stream()
                .map(this::toResponse)
                .toList();
    }
    private Supplier findSupplier(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Supplier not found"));
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeEmail(String email) {
        String normalized = normalize(email);
        return normalized == null ? null : normalized.toLowerCase();
    }

    private SupplierResponse toResponse(Supplier supplier) {
        return new SupplierResponse(
                supplier.getId(),
                supplier.getSupplierName(),
                supplier.getContactPerson(),
                supplier.getPhone(),
                supplier.getEmail(),
                supplier.getAddress(),
                supplier.isActive(),
                supplier.getCreatedAt(),
                supplier.getUpdatedAt()
        );
    }
}
