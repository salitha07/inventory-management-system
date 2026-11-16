package com.bajaj.inventory_backend.inventory.dto;

import com.bajaj.inventory_backend.inventory.domain.MovementKind;
import java.time.Instant;
import java.util.Objects;

/**
 * Provisional, storage-independent internal DTOs. No persistence or authorization occurs here.
 * Constructors validate even when Jakarta Bean Validation is not invoked.
 */
public final class InventoryContracts {
    private InventoryContracts() { }

    private static void positive(Long id, String name) {
        if (id == null || id <= 0) throw new IllegalArgumentException(name + " must be positive");
    }

    private static void optionalId(Long id, String name) {
        if (id != null) positive(id, name);
    }

    private static void text(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }

    /** Created only after authentication by trusted orchestration; not an authentication proof. */
    public record AuthenticatedActor(Long userId) {
        public AuthenticatedActor { positive(userId, "userId"); }
    }

    /** Reference vocabulary and manual reference nullability remain provisional. */
    public record MovementContext(String referenceType, Long referenceId, String reason,
                                  AuthenticatedActor actor) {
        public MovementContext {
            text(referenceType, "referenceType");
            optionalId(referenceId, "referenceId");
            text(reason, "reason");
            Objects.requireNonNull(actor, "actor");
        }
    }

    /** Positive input quantity for receive/deduct/return; never a signed delta. */
    public record StockChangeCommand(Long productId, Integer quantity, MovementContext context) {
        public StockChangeCommand {
            positive(productId, "productId");
            if (quantity == null || quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
            Objects.requireNonNull(context, "context");
            positive(context.referenceId(), "referenceId");
        }
    }

    /** Signed-delta interpretation is provisional; no balance is changed by this DTO. */
    public record ManualAdjustmentCommand(Long productId, Integer quantityChange, MovementContext context) {
        public ManualAdjustmentCommand {
            positive(productId, "productId");
            if (quantityChange == null || quantityChange == 0) throw new IllegalArgumentException("change must be nonzero");
            Objects.requireNonNull(context, "context");
        }
    }

    /** A snapshot, not an independently writable balance. Unknown threshold stays null. */
    public record CurrentStockView(Long productId, String productCode, String productName,
                                   Long categoryId, boolean active, int quantity, Integer reorderLevel) {
        public CurrentStockView {
            positive(productId, "productId");
            positive(categoryId, "categoryId");
            text(productCode, "productCode");
            text(productName, "productName");
            if (quantity < 0 || (reorderLevel != null && reorderLevel < 0)) {
                throw new IllegalArgumentException("Stock and threshold must be nonnegative");
            }
        }
    }

    /** lowStockOnly requests filtering; comparison/default policy awaits team approval. */
    public record CurrentStockQuery(String text, Long categoryId, Boolean active, boolean lowStockOnly) {
        public CurrentStockQuery { optionalId(categoryId, "categoryId"); }
    }

    public record StockMovementView(Long movementId, Long productId, MovementKind movementType,
                                    int quantityChange, int quantityAfter, MovementContext context,
                                    Instant createdAt) {
        public StockMovementView {
            positive(movementId, "movementId");
            positive(productId, "productId");
            Objects.requireNonNull(movementType, "movementType").validateChange(quantityChange);
            if (quantityAfter < 0) throw new IllegalArgumentException("quantityAfter must be nonnegative");
            long before = (long) quantityAfter - quantityChange;
            if (before < 0 || before > Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid prior quantity");
            Objects.requireNonNull(context, "context");
            if (movementType != MovementKind.MANUAL_ADJUSTMENT) positive(context.referenceId(), "referenceId");
            Objects.requireNonNull(createdAt, "createdAt");
        }
    }

    /** Proposed inclusive from / exclusive to interval; no repository query is executed. */
    public record StockMovementQuery(Long productId, MovementKind movementType, String referenceType,
                                     Long referenceId, Long createdBy, Instant from, Instant to) {
        public StockMovementQuery {
            optionalId(productId, "productId");
            optionalId(referenceId, "referenceId");
            optionalId(createdBy, "createdBy");
            if (referenceType != null) text(referenceType, "referenceType");
            if (from != null && to != null && !from.isBefore(to)) {
                throw new IllegalArgumentException("from must precede to");
            }
        }
    }

    /** Proposed page limits; sorting and Spring Data exposure remain deferred. */
    public record PageQuery(int page, int size) {
        public PageQuery {
            if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid page or size");
        }
    }
}
