package com.bajaj.inventory_backend.inventory.persistence.jpa;

import com.bajaj.inventory_backend.inventory.core.InventoryPorts.MovementWrite;
import com.bajaj.inventory_backend.inventory.domain.MovementKind;
import com.bajaj.inventory_backend.inventory.dto.InventoryContracts.*;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "stock_movements")
class StockMovement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "movement_type", length = 32, nullable = false, updatable = false)
    private MovementKind movementType;

    @Column(name = "quantity_change", nullable = false, updatable = false)
    private int quantityChange;

    @Column(name = "quantity_after", nullable = false, updatable = false)
    private int quantityAfter;

    @Column(name = "reference_type", length = 64, nullable = false, updatable = false)
    private String referenceType;

    @Column(name = "reference_id", updatable = false)
    private Long referenceId;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant createdAt;

    protected StockMovement() { }

    StockMovement(MovementWrite write) {
        productId = write.productId();
        movementType = write.kind();
        quantityChange = write.change();
        quantityAfter = write.quantityAfter();
        referenceType = write.context().referenceType();
        referenceId = write.context().referenceId();
        reason = write.context().reason();
        createdBy = write.context().actor().userId();
        createdAt = write.createdAt();
    }

    StockMovementView view() {
        return new StockMovementView(id, productId, movementType, quantityChange, quantityAfter,
                new MovementContext(referenceType, referenceId, reason, new AuthenticatedActor(createdBy)), createdAt);
    }
}
