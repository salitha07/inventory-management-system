package com.bajaj.inventory_backend.sales.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Line item request DTO for checkout.
 *
 * <p>IMPORTANT ZERO-TRUST RULE:
 * The server never trusts client-submitted prices or totals.
 * Any client price hint is discarded; prices are loaded authoritatively by the backend.
 */
public class SaleItemRequest {

    @NotNull(message = "Product ID is required")
    @Positive(message = "Product ID must be a positive integer")
    private Long productId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    /**
     * Optional client price hint.
     * The server ignores this value and queries authoritative pricing from the database.
     */
    private BigDecimal unitSellingPrice;

    public SaleItemRequest() {
    }

    public SaleItemRequest(Long productId, Integer quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public SaleItemRequest(Long productId, Integer quantity, BigDecimal unitSellingPrice) {
        this.productId = productId;
        this.quantity = quantity;
        this.unitSellingPrice = unitSellingPrice;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitSellingPrice() {
        return unitSellingPrice;
    }

    public void setUnitSellingPrice(BigDecimal unitSellingPrice) {
        this.unitSellingPrice = unitSellingPrice;
    }
}

