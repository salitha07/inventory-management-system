package com.bajaj.inventory_backend.inventory.service;

import com.bajaj.inventory_backend.common.exception.InsufficientStockException;
import com.bajaj.inventory_backend.inventory.core.InventoryOperations;
import com.bajaj.inventory_backend.inventory.core.InventoryQueries;
import com.bajaj.inventory_backend.inventory.dto.InventoryContracts.AuthenticatedActor;
import com.bajaj.inventory_backend.inventory.dto.InventoryContracts.CurrentStockView;
import com.bajaj.inventory_backend.inventory.dto.InventoryContracts.MovementContext;
import com.bajaj.inventory_backend.inventory.dto.InventoryContracts.StockChangeCommand;
import com.bajaj.inventory_backend.inventory.entity.StockMovementType;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SalesInventoryServiceTest {
    private final InventoryQueries queries = mock(InventoryQueries.class);
    private final InventoryOperations operations = mock(InventoryOperations.class);
    private final SalesInventoryService.SalesContext salesContext = mock(SalesInventoryService.SalesContext.class);
    private final InventoryService service = new SalesInventoryService(queries, operations, salesContext);

    @Test
    void mapsDeductionFailureUsingBalanceFromTheLockedOperation() {
        var context = new MovementContext("SALE", 3L, "Checkout", new AuthenticatedActor(4L));
        when(salesContext.deductionContext(3L)).thenReturn(context);
        when(queries.getCurrentStock(1L)).thenReturn(new CurrentStockView(1L, "P1", "Part", 2L, true, 8, null));
        when(operations.deductStock(new StockChangeCommand(1L, 5, context)))
                .thenThrow(new InventoryOperations.InsufficientStock(1L, 2, 5));

        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            var failure = assertThrows(InsufficientStockException.class,
                    () -> service.deductStock(1L, 5, StockMovementType.SALE, 3L));
            assertEquals(2, failure.getAvailableQuantity());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void rejectsDeductionWithoutCallerTransactionAndRestorationWithoutPolicy() {
        assertThrows(IllegalStateException.class,
                () -> service.deductStock(1L, 5, StockMovementType.SALE, 3L));
        assertThrows(UnsupportedOperationException.class,
                () -> service.restoreStock(1L, 5, StockMovementType.RETURN, 3L));
        verifyNoInteractions(queries, operations, salesContext);
    }
}
