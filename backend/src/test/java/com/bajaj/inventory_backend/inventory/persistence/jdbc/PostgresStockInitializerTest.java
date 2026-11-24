package com.bajaj.inventory_backend.inventory.persistence.jdbc;

import com.bajaj.inventory_backend.inventory.persistence.StockPersistenceException;
import com.bajaj.inventory_backend.inventory.persistence.StockPersistenceException.Reason;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PostgresStockInitializerTest {
    private static final String PRODUCT = "SELECT id FROM products WHERE id = ? FOR KEY SHARE";
    private static final String INSERT = """
            INSERT INTO inventory_stock (product_id, quantity, reorder_level)
            VALUES (?, 0, NULL)
            ON CONFLICT (product_id) DO NOTHING
            """;

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final SimpleTransactionStatus transaction = new SimpleTransactionStatus();
    private final PostgresStockInitializer initializer = new PostgresStockInitializer(jdbc, new TransactionTemplate(manager));

    @Test
    void insertsAnEmptyBalanceAndDoesNotOverwriteItOnRepeat() {
        when(manager.getTransaction(any())).thenReturn(transaction);
        when(jdbc.queryForObject(PRODUCT, Long.class, 1L)).thenReturn(1L);
        when(jdbc.update(INSERT, 1L)).thenReturn(1, 0);

        assertEquals(PostgresStockInitializer.Result.INITIALIZED, initializer.initializeStock(1L));
        assertEquals(PostgresStockInitializer.Result.ALREADY_INITIALIZED, initializer.initializeStock(1L));

        var calls = inOrder(manager, jdbc);
        for (int attempt = 0; attempt < 2; attempt++) {
            calls.verify(manager).getTransaction(argThat(definition ->
                    definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRED
                            && !definition.isReadOnly()));
            calls.verify(jdbc).queryForObject(PRODUCT, Long.class, 1L);
            calls.verify(jdbc).update(INSERT, 1L);
            calls.verify(manager).commit(transaction);
        }
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void rollsBackWithoutInsertingWhenProductIsMissing() {
        when(manager.getTransaction(any())).thenReturn(transaction);
        when(jdbc.queryForObject(PRODUCT, Long.class, 1L)).thenThrow(new EmptyResultDataAccessException(1));

        assertEquals(Reason.PRODUCT_NOT_FOUND,
                assertThrows(StockPersistenceException.class, () -> initializer.initializeStock(1L)).reason());
        verify(jdbc).queryForObject(PRODUCT, Long.class, 1L);
        verifyNoMoreInteractions(jdbc);
        verify(manager).rollback(transaction);
    }

    @Test
    void rejectsReadOnlyTransactionBeforeSql() {
        when(manager.getTransaction(any())).thenReturn(transaction);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        try {
            assertEquals(Reason.READ_ONLY,
                    assertThrows(StockPersistenceException.class, () -> initializer.initializeStock(1L)).reason());
        } finally {
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        }
        verifyNoInteractions(jdbc);
        verify(manager).rollback(transaction);
    }

    @Test
    void rejectsInvalidProductIdWithoutStartingTransaction() {
        assertThrows(IllegalArgumentException.class, () -> initializer.initializeStock(null));
        assertThrows(IllegalArgumentException.class, () -> initializer.initializeStock(0L));
        verifyNoInteractions(manager, jdbc);
    }
}
