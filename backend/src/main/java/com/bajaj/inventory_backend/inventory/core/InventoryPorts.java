package com.bajaj.inventory_backend.inventory.core;

import com.bajaj.inventory_backend.inventory.domain.MovementKind;
import com.bajaj.inventory_backend.inventory.dto.InventoryContracts.*;
import java.time.Instant;
import java.util.function.IntFunction;

/** Inventory-owned ports, provisional until persistence and shared contracts are approved. */
public final class InventoryPorts {
    private InventoryPorts() { }

    /** An immutable write plan, not a persisted movement or a success response. */
    public record MovementWrite(Long productId, MovementKind kind, int change, int quantityAfter,
                                MovementContext context, Instant createdAt) {
        public MovementWrite {
            // Reuse snapshot invariants; the placeholder ID is validated but never persisted.
            new StockMovementView(1L, productId, kind, change, quantityAfter, context, createdAt);
        }
    }

    /**
     * Combined balance/movement persistence boundary: do not split into independent writes.
     * Load an existing authoritative balance under a database write lock, invoke planner,
     * save its after-balance plus movement, and return the generated movement snapshot.
     * Reject missing Product/stock; never infer zero or silently initialize.
     * Any planner, write, flush or commit failure must roll back BOTH writes and propagate.
     * A PostgreSQL adapter must join the caller's REQUIRED transaction, hold its row lock
     * until transaction completion, and mark joined transactions rollback-only on failure.
     * Return within an outer transaction is provisional until that caller commits.
     * No independent REQUIRES_NEW transaction and no retry of ambiguous commit outcomes.
     */
    public interface AtomicStockStore {
        StockMovementView apply(Long productId, IntFunction<MovementWrite> planner);
    }

    /**
     * Trusted integration must reject an absent/unauthorized principal or mismatched actor.
     * Return verification must check approved/restockable source line and allowed quantity.
     * A boolean supplied by an HTTP client is not sufficient approval.
     * Event eligibility/remaining quantity must be protected in the same transaction as apply
     * when concurrent returns are possible. Implementations throw on rejection.
     */
    public interface OperationPolicy {
        void requireTrustedActor(AuthenticatedActor actor);
        void requireApprovedRestockableReturn(StockChangeCommand command);
    }
}
