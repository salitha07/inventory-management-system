package com.bajaj.inventory_backend.inventory.persistence.jpa;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "inventory_stock")
class InventoryStock {
    @Id
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "reorder_level")
    private Integer reorderLevel;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant updatedAt;

    protected InventoryStock() { }

    int quantity() {
        return quantity;
    }

    void applyBalance(int after, Instant changedAt) {
        quantity = after;
        updatedAt = changedAt;
    }
}
