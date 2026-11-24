
package com.bajaj.inventory_backend.dto.product;

import java.math.BigDecimal;
import java.time.Instant;

public class ProductResponseDTO {

    private Long id;
    private String productCode;
    private String productName;
    private String description;
    private Long categoryId;
    private String categoryName;
    private BigDecimal costPrice;
    private BigDecimal sellingPrice;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public ProductResponseDTO() {
    }

    public ProductResponseDTO(
            Long id,
            String productCode,
            String productName,
            String description,
            Long categoryId,
            String categoryName,
            BigDecimal costPrice,
            BigDecimal sellingPrice,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.productCode = productCode;
        this.productName = productName;
        this.description = description;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.costPrice = costPrice;
        this.sellingPrice = sellingPrice;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getProductCode() {
        return productCode;
    }

    public String getProductName() {
        return productName;
    }

    public String getDescription() {
        return description;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public BigDecimal getSellingPrice() {
        return sellingPrice;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
