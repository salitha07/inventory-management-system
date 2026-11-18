package com.bajaj.inventory_backend.inventory;

import com.bajaj.inventory_backend.inventory.domain.MovementKind;
import com.bajaj.inventory_backend.inventory.dto.InventoryContracts.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

/** Plain unit tests: no Spring context, datasource, Flyway or database access. */
class InventoryFoundationTest {
    private final MovementContext context = new MovementContext("SALE", 7L, "Sale checkout", new AuthenticatedActor(1L));

    @Test void signedChangesAndExactDepletion() {
        assertEquals(0, MovementKind.SALE_DEDUCT.quantityAfter(3, -3));
        assertEquals(5, MovementKind.PURCHASE_RECEIVE.quantityAfter(3, 2));
        assertEquals(5, MovementKind.CUSTOMER_RETURN.quantityAfter(3, 2));
        assertEquals(2, MovementKind.MANUAL_ADJUSTMENT.quantityAfter(3, -1));
        assertEquals(4, MovementKind.MANUAL_ADJUSTMENT.quantityAfter(3, 1));
    }

    @Test void rejectsContradictorySignsZeroNegativeBalancesAndOverflow() {
        for (MovementKind kind : MovementKind.values()) {
            assertThrows(IllegalArgumentException.class, () -> kind.quantityAfter(1, 0));
            assertThrows(IllegalArgumentException.class, () -> kind.quantityAfter(-1, 1));
        }
        assertThrows(IllegalArgumentException.class, () -> MovementKind.SALE_DEDUCT.quantityAfter(3, 1));
        assertThrows(IllegalArgumentException.class, () -> MovementKind.PURCHASE_RECEIVE.quantityAfter(3, -1));
        assertThrows(IllegalArgumentException.class, () -> MovementKind.CUSTOMER_RETURN.quantityAfter(3, -1));
        assertThrows(IllegalArgumentException.class, () -> MovementKind.SALE_DEDUCT.quantityAfter(3, -4));
        assertThrows(ArithmeticException.class, () -> MovementKind.MANUAL_ADJUSTMENT.quantityAfter(Integer.MAX_VALUE, 1));
        assertThrows(IllegalArgumentException.class, () -> MovementKind.MANUAL_ADJUSTMENT.quantityAfter(0, Integer.MIN_VALUE));
    }

    @Test void commandsRequireIdentityQuantityAndTraceableMetadata() {
        assertEquals(2, new StockChangeCommand(1L, 2, context).quantity());
        assertThrows(IllegalArgumentException.class, () -> new StockChangeCommand(null, 2, context));
        assertThrows(IllegalArgumentException.class, () -> new StockChangeCommand(1L, null, context));
        assertThrows(IllegalArgumentException.class, () -> new StockChangeCommand(1L, -2, context));
        assertThrows(IllegalArgumentException.class, () -> new AuthenticatedActor(0L));
        assertThrows(IllegalArgumentException.class, () -> new MovementContext("SALE", 1L, " ", context.actor()));
        assertThrows(NullPointerException.class, () -> new MovementContext("SALE", 1L, "reason", null));
        MovementContext manual = new MovementContext("MANUAL", null, "Stock count", context.actor());
        assertThrows(IllegalArgumentException.class, () -> new StockChangeCommand(1L, 1, manual));
        assertEquals(-1, new ManualAdjustmentCommand(1L, -1, manual).quantityChange());
        assertThrows(IllegalArgumentException.class, () -> new ManualAdjustmentCommand(1L, 0, manual));
    }

    @Test void movementSnapshotsRejectImpossibleBeforeAndAfterBalances() {
        assertEquals(0, new StockMovementView(1L, 1L, MovementKind.SALE_DEDUCT, -2, 0, context, Instant.EPOCH).quantityAfter());
        assertThrows(IllegalArgumentException.class, () -> new StockMovementView(1L, 1L, MovementKind.CUSTOMER_RETURN, 2, 1, context, Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> new StockMovementView(1L, 1L, MovementKind.SALE_DEDUCT, -1, Integer.MAX_VALUE, context, Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> new StockMovementView(1L, 1L, MovementKind.SALE_DEDUCT, -1, -1, context, Instant.EPOCH));
    }

    @Test void queryAndSnapshotBoundariesStayExplicit() {
        assertNull(new CurrentStockView(1L, "P01", "Part", 1L, true, 0, null).reorderLevel());
        assertThrows(IllegalArgumentException.class, () -> new CurrentStockView(1L, "P01", "Part", 1L, true, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new CurrentStockQuery(null, -1L, null, true));
        assertDoesNotThrow(() -> new StockMovementQuery(null, null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new StockMovementQuery(null, null, null, null, null, Instant.EPOCH, Instant.EPOCH));
        assertDoesNotThrow(() -> new PageQuery(0, 100));
        assertThrows(IllegalArgumentException.class, () -> new PageQuery(-1, 20));
        assertThrows(IllegalArgumentException.class, () -> new PageQuery(0, 101));
    }
}
