
package com.bajaj.inventory_backend.purchase;

import com.bajaj.inventory_backend.purchase.dto.CreatePurchaseRequest;
import com.bajaj.inventory_backend.purchase.dto.PurchaseResponse;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/purchases")
public class PurchaseController {

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @GetMapping
    public List<PurchaseResponse> getAllPurchases() {
        return purchaseService.getAllPurchases();
    }

    @GetMapping("/{id}")
    public PurchaseResponse getPurchaseById(@PathVariable Long id) {
        return purchaseService.getPurchaseById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseResponse createPurchase(
            @Valid @RequestBody CreatePurchaseRequest request,
            org.springframework.security.core.Authentication authentication
    ) {

        // Authentication integration must provide the database user ID.
        // Replace this after confirming Member 1's security principal.
        throw new UnsupportedOperationException(
                "Purchase creation requires authenticated user ID integration"
        );
    }
}
