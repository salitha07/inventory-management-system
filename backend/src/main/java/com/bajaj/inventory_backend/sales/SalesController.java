package com.bajaj.inventory_backend.sales;

import com.bajaj.inventory_backend.sales.dto.ReceiptResponse;
import com.bajaj.inventory_backend.sales.dto.SaleCreateRequest;
import com.bajaj.inventory_backend.sales.dto.SaleResponse;
import com.bajaj.inventory_backend.sales.dto.SaleSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for the Sales / Point of Sale (POS) module.
 *
 * <p>Endpoints follow the approved POS API contract at {@code /api/v1/sales}.
 */
@RestController
@RequestMapping("/api/v1/sales")
public class SalesController {

    private final SalesService salesService;

    public SalesController(SalesService salesService) {
        this.salesService = salesService;
    }

    // ──────────────────────────────────────────────
    //  Checkout — POST /api/v1/sales
    // ──────────────────────────────────────────────

    /**
     * Atomically executes checkout, freezes unit cost snapshots, decrements
     * stock via InventoryService, commits financial invoice records, and returns
     * completed sale details.
     *
     * @param request client checkout request payload
     * @param authentication current cashier authentication
     * @return 201 Created with SaleResponse details
     */
    @PostMapping
    public ResponseEntity<SaleResponse> checkout(
            @Valid @RequestBody SaleCreateRequest request,
            Authentication authentication) {
        SaleResponse response = salesService.processCheckout(request, authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ──────────────────────────────────────────────
    //  Sale Details — GET /api/v1/sales/{id}
    // ──────────────────────────────────────────────

    /**
     * Retrieve complete metadata and line-item details of a specific sale.
     *
     * @param id sale primary key
     * @return the sale details DTO if found, or 404
     */
    @GetMapping("/{id}")
    public ResponseEntity<SaleResponse> getSaleById(@PathVariable Long id) {
        return salesService.getSaleDetails(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ──────────────────────────────────────────────
    //  Sales History — GET /api/v1/sales
    // ──────────────────────────────────────────────

    /**
     * Retrieve a paginated list of sales summaries with optional filters.
     *
     * @param status optional status filter (e.g. COMPLETED)
     * @param paymentMethod optional payment method filter (e.g. CASH, CARD)
     * @param search optional sale number search term
     * @param pageable pagination and sort parameters (default: page 0, size 20, saleDate desc)
     * @return paginated sales summaries
     */
    @GetMapping
    public ResponseEntity<Page<SaleSummaryResponse>> getSalesHistory(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "saleDate",
                    direction = org.springframework.data.domain.Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(salesService.findSalesSummary(status, paymentMethod, search, pageable));
    }

    // ──────────────────────────────────────────────
    //  Receipt — GET /api/v1/sales/{id}/receipt
    // ──────────────────────────────────────────────

    /**
     * Retrieve formatted receipt data optimized for 80 mm thermal printing and invoice rendering.
     *
     * @param id sale primary key
     * @return formatted receipt response DTO if found, or 404
     */
    @GetMapping("/{id}/receipt")
    public ResponseEntity<ReceiptResponse> getReceipt(@PathVariable Long id) {
        return salesService.getReceipt(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}


