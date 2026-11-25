package com.bajaj.inventory_backend.inventory.core;

import com.bajaj.inventory_backend.inventory.core.InventoryPorts.*;
import com.bajaj.inventory_backend.inventory.domain.MovementKind;
import com.bajaj.inventory_backend.inventory.dto.InventoryContracts.*;
import java.time.Clock;
import java.util.Objects;

/** Plain Java core. No Spring bean, persistence implementation or shared Sales interface. */
public final class InventoryOperations {
    private final AtomicStockStore store;
    private final OperationPolicy policy;
    private final Clock clock;

    public InventoryOperations(AtomicStockStore store, OperationPolicy policy, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public StockMovementView receiveStock(StockChangeCommand command) {
        Objects.requireNonNull(command, "command");
        return change(command.productId(), command.quantity(), MovementKind.PURCHASE_RECEIVE, command.context(), null);
    }

    public StockMovementView deductStock(StockChangeCommand command) {
        Objects.requireNonNull(command, "command");
        return change(command.productId(), -command.quantity(), MovementKind.SALE_DEDUCT, command.context(), null);
    }

    public StockMovementView returnStock(StockChangeCommand command) {
        Objects.requireNonNull(command, "command");
        return change(command.productId(), command.quantity(), MovementKind.CUSTOMER_RETURN, command.context(), command);
    }

    public StockMovementView manualAdjustment(ManualAdjustmentCommand command) {
        Objects.requireNonNull(command, "command");
        return change(command.productId(), command.quantityChange(), MovementKind.MANUAL_ADJUSTMENT, command.context(), null);
    }

    private StockMovementView change(Long productId, int delta, MovementKind kind,
                                     MovementContext context, StockChangeCommand customerReturn) {
        return store.apply(productId, before -> {
            policy.requireTrustedActor(context.actor());
            if (customerReturn != null) policy.requireApprovedRestockableReturn(customerReturn);
            if (kind == MovementKind.SALE_DEDUCT && before >= 0 && (long) before + delta < 0) {
                throw new InsufficientStock(productId, before, -delta);
            }
            int after = kind.quantityAfter(before, delta);
            return new MovementWrite(productId, kind, delta, after, context, clock.instant());
        });
    }

    public static final class InsufficientStock extends IllegalArgumentException {
        private final int available;

        public InsufficientStock(Long productId, int available, int requested) {
            super("Insufficient stock for product " + productId + ": available=" + available + ", requested=" + requested);
            this.available = available;
        }

        public int available() {
            return available;
        }
    }
}
