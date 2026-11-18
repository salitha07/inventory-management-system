package com.bajaj.inventory_backend.inventory;

import com.bajaj.inventory_backend.inventory.core.InventoryOperations;
import com.bajaj.inventory_backend.inventory.core.InventoryPorts.*;
import com.bajaj.inventory_backend.inventory.domain.MovementKind;
import com.bajaj.inventory_backend.inventory.dto.InventoryContracts.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.function.IntFunction;
import static org.junit.jupiter.api.Assertions.*;

class InventoryOperationsTest {
    private final MemoryStore store = new MemoryStore();
    private final TestPolicy policy = new TestPolicy();
    private final Instant now = Instant.parse("2026-11-16T12:00:00Z");
    private final InventoryOperations operations = new InventoryOperations(store, policy, Clock.fixed(now, ZoneOffset.UTC));

    private MovementContext context(String reference) {
        return new MovementContext(reference, 9L, "Verified business event", new AuthenticatedActor(2L));
    }
    private StockChangeCommand command(String reference, int quantity) {
        return new StockChangeCommand(1L, quantity, context(reference));
    }

    @Test void receivePreservesMetadataAndAddsStock() {
        StockMovementView result = operations.receiveStock(command("PURCHASE", 3));
        assertEquals(8, store.quantity);
        assertEquals(MovementKind.PURCHASE_RECEIVE, result.movementType());
        assertEquals(3, result.quantityChange());
        assertEquals(8, result.quantityAfter());
        assertEquals(1L, result.productId());
        assertEquals(context("PURCHASE"), result.context());
        assertEquals(now, result.createdAt());
        assertEquals(List.of(result), store.movements);
    }

    @Test void deductAllowsExactDepletionAndRejectsOversellingWithoutWrites() {
        StockMovementView result = operations.deductStock(command("SALE", 5));
        assertEquals(-5, result.quantityChange());
        assertEquals(0, store.quantity);
        assertThrows(InventoryOperations.InsufficientStock.class, () -> operations.deductStock(command("SALE", 1)));
        assertEquals(0, store.quantity);
        assertEquals(1, store.movements.size());
    }

    @Test void returnsRequireApprovedRestockableSource() {
        policy.returnAllowed = false;
        assertThrows(IllegalArgumentException.class, () -> operations.returnStock(command("CUSTOMER_RETURN", 2)));
        assertUnchanged();
        policy.returnAllowed = true;
        StockMovementView result = operations.returnStock(command("CUSTOMER_RETURN", 2));
        assertEquals(7, store.quantity);
        assertEquals(MovementKind.CUSTOMER_RETURN, result.movementType());
        assertEquals(2, result.quantityChange());
        assertEquals(context("CUSTOMER_RETURN"), result.context());
    }

    @Test void manualChangesAreSignedAndCannotMakeStockNegative() {
        MovementContext manual = new MovementContext("MANUAL", null, "Count correction", new AuthenticatedActor(2L));
        assertEquals(7, operations.manualAdjustment(new ManualAdjustmentCommand(1L, 2, manual)).quantityAfter());
        StockMovementView result = operations.manualAdjustment(new ManualAdjustmentCommand(1L, -7, manual));
        assertEquals(MovementKind.MANUAL_ADJUSTMENT, result.movementType());
        assertEquals(-7, result.quantityChange());
        assertEquals(manual, result.context());
        assertThrows(IllegalArgumentException.class, () -> operations.manualAdjustment(new ManualAdjustmentCommand(1L, -1, manual)));
        assertEquals(0, store.quantity);
        assertEquals(2, store.movements.size());
    }

    @Test void invalidCommandsNeverReachPersistence() {
        for (Integer invalid : Arrays.asList(null, 0, -1, Integer.MIN_VALUE)) {
            assertThrows(IllegalArgumentException.class, () -> operations.receiveStock(new StockChangeCommand(1L, invalid, context("PURCHASE"))));
            assertThrows(IllegalArgumentException.class, () -> operations.deductStock(new StockChangeCommand(1L, invalid, context("SALE"))));
            assertThrows(IllegalArgumentException.class, () -> operations.returnStock(new StockChangeCommand(1L, invalid, context("RETURN"))));
        }
        assertThrows(IllegalArgumentException.class, () -> operations.manualAdjustment(new ManualAdjustmentCommand(1L, 0, context("MANUAL"))));
        assertThrows(IllegalArgumentException.class, () -> new MovementContext(" ", 1L, "reason", new AuthenticatedActor(2L)));
        assertThrows(IllegalArgumentException.class, () -> new MovementContext("SALE", -1L, "reason", new AuthenticatedActor(2L)));
        assertThrows(NullPointerException.class, () -> operations.receiveStock(null));
        assertEquals(0, store.calls);
        assertUnchanged();
    }

    @Test void untrustedActorAndMissingStockRejectWithoutMutation() {
        policy.actorAllowed = false;
        assertThrows(SecurityException.class, () -> operations.receiveStock(command("PURCHASE", 1)));
        assertUnchanged();
        policy.actorAllowed = true;
        store.exists = false;
        assertThrows(IllegalStateException.class, () -> operations.receiveStock(command("PURCHASE", 1)));
        assertUnchanged();
    }

    @Test void overflowRejectsAllPositiveOperationsWithoutWrites() {
        store.quantity = Integer.MAX_VALUE;
        assertThrows(ArithmeticException.class, () -> operations.receiveStock(command("PURCHASE", 1)));
        assertThrows(ArithmeticException.class, () -> operations.returnStock(command("RETURN", 1)));
        assertThrows(ArithmeticException.class, () -> operations.manualAdjustment(new ManualAdjustmentCommand(1L, 1, context("MANUAL"))));
        assertEquals(Integer.MAX_VALUE, store.quantity);
        assertTrue(store.movements.isEmpty());
    }

    @Test void movementWriteAndCommitFailuresRollBackBothSidesInAdapterDouble() {
        for (String stage : List.of("balance", "movement", "commit")) {
            store.failure = stage;
            assertThrows(IllegalStateException.class, () -> operations.receiveStock(command("PURCHASE", 2)));
            assertUnchanged();
        }
    }

    private void assertUnchanged() {
        assertEquals(5, store.quantity);
        assertTrue(store.movements.isEmpty());
    }

    private static final class TestPolicy implements OperationPolicy {
        boolean actorAllowed = true;
        boolean returnAllowed = true;
        public void requireTrustedActor(AuthenticatedActor actor) {
            if (!actorAllowed || actor.userId() != 2L) throw new SecurityException("Actor not authorized");
        }
        public void requireApprovedRestockableReturn(StockChangeCommand command) {
            if (!returnAllowed) throw new IllegalArgumentException("Return not approved/restockable");
        }
    }

    /** Test-only transactional staging; not a production store or cross-process lock. */
    private static final class MemoryStore implements AtomicStockStore {
        int quantity = 5;
        int calls;
        boolean exists = true;
        String failure;
        final List<StockMovementView> movements = new ArrayList<>();
        public StockMovementView apply(Long productId, IntFunction<MovementWrite> planner) {
            calls++;
            if (!exists) throw new IllegalStateException("Stock not initialized");
            MovementWrite write = planner.apply(quantity);
            int stagedQuantity = write.quantityAfter();
            if ("balance".equals(failure)) throw new IllegalStateException("Simulated balance failure");
            if ("movement".equals(failure)) throw new IllegalStateException("Simulated movement failure");
            StockMovementView staged = new StockMovementView((long) movements.size() + 1, productId,
                    write.kind(), write.change(), write.quantityAfter(), write.context(), write.createdAt());
            if (failure != null) throw new IllegalStateException("Simulated " + failure + " failure");
            quantity = stagedQuantity;
            movements.add(staged);
            return staged;
        }
    }
}
