
package com.bajaj.inventory_backend.purchase;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "purchase_items")
public class PurchaseItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id", nullable = false)
    private Purchase purchase;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_cost", nullable = false,
            precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "line_total", nullable = false,
            precision = 12, scale = 2)
    private BigDecimal lineTotal;

    public PurchaseItem() {}

    // Getters and setters can be generated using IntelliJ.
}
