
package com.bajaj.inventory_backend.dto.category;

import java.time.Instant;

public class CategoryResponseDTO {

    private Long id;
    private String categoryName;
    private String description;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public CategoryResponseDTO() {
    }

    public CategoryResponseDTO(
            Long id,
            String categoryName,
            String description,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.categoryName = categoryName;
        this.description = description;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getDescription() {
        return description;
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
