package com.bajaj.inventory_backend.purchase;

import com.bajaj.inventory_backend.entity.User;
import com.bajaj.inventory_backend.repository.UserRepository;
import com.bajaj.inventory_backend.purchase.dto.CreatePurchaseRequest;
import com.bajaj.inventory_backend.purchase.dto.PurchaseResponse;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.List;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/purchases")
public class PurchaseController {

    private final PurchaseService purchaseService;
    private final UserRepository userRepository;

    public PurchaseController(
            PurchaseService purchaseService,
            UserRepository userRepository
    ) {
        this.purchaseService = purchaseService;
        this.userRepository = userRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'INVENTORY_STAFF')")
    public List<PurchaseResponse> getAllPurchases() {

        return purchaseService.getAllPurchases();
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'INVENTORY_STAFF')")
    public List<PurchaseResponse> searchPurchases(
            @RequestParam(required = false) String purchaseNumber,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) PurchaseStatus status,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate
    ) {

        return purchaseService.searchPurchases(
                purchaseNumber,
                supplierId,
                status,
                fromDate,
                toDate
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'INVENTORY_STAFF')")
    public PurchaseResponse getPurchaseById(
            @PathVariable Long id
    ) {

        return purchaseService.getPurchaseById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'INVENTORY_STAFF')")
    public PurchaseResponse createPurchase(
            @Valid @RequestBody CreatePurchaseRequest request,
            Authentication authentication
    ) {

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user not found"
                ));

        return purchaseService.createPurchase(
                request,
                user.getId()
        );
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public PurchaseResponse cancelPurchase(
            @PathVariable Long id
    ) {

        return purchaseService.cancelPurchase(id);
    }
}