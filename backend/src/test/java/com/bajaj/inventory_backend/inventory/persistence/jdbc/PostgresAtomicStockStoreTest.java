package com.bajaj.inventory_backend.inventory.persistence.jdbc;

import com.bajaj.inventory_backend.inventory.core.InventoryPorts.MovementWrite;
import com.bajaj.inventory_backend.inventory.domain.MovementKind;
import com.bajaj.inventory_backend.inventory.dto.InventoryContracts.*;
import com.bajaj.inventory_backend.inventory.persistence.StockPersistenceException;
import com.bajaj.inventory_backend.inventory.persistence.StockPersistenceException.Reason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.*;
import org.springframework.jdbc.core.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.sql.ResultSet;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Mocks test protocol and error propagation, not database locks/rollback. */
class PostgresAtomicStockStoreTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final TransactionStatus status = new SimpleTransactionStatus();
    private final TransactionTemplate template = new TransactionTemplate(manager);
    private final PostgresAtomicStockStore store = new PostgresAtomicStockStore(jdbc, template);
    private final Instant now = Instant.parse("2026-11-16T12:00:00Z");
    private final MovementContext context = new MovementContext("SALE", 7L, "Checkout", new AuthenticatedActor(2L));

    PostgresAtomicStockStoreTest() {
        when(manager.getTransaction(any())).thenReturn(status);
    }

    @AfterEach void clearReadOnlyFlag() { TransactionSynchronizationManager.setCurrentTransactionReadOnly(false); }

    private MovementWrite plan(int before) {
        return new MovementWrite(1L, MovementKind.SALE_DEDUCT, -2, before - 2, context, now);
    }
    private void existing() {
        when(jdbc.queryForObject(PostgresAtomicStockStore.PRODUCT, Long.class, 1L)).thenReturn(1L);
        when(jdbc.queryForObject(PostgresAtomicStockStore.LOCK, Integer.class, 1L)).thenReturn(5);
    }
    private void failure(Reason reason, IntFunction<MovementWrite> planner) {
        assertEquals(reason, assertThrows(StockPersistenceException.class, () -> store.apply(1L, planner)).reason());
        verify(manager).rollback(status);
        verify(manager, never()).commit(any());
    }

    @Test void missingProductIsDistinctAndNeverLocksStock() {
        when(jdbc.queryForObject(PostgresAtomicStockStore.PRODUCT, Long.class, 1L))
                .thenThrow(new EmptyResultDataAccessException(1));
        failure(Reason.PRODUCT_NOT_FOUND, this::plan);
        verify(jdbc, never()).queryForObject(PostgresAtomicStockStore.LOCK, Integer.class, 1L);
    }

    @Test void missingStockDoesNotInitializeOrInvokePlanner() {
        existing();
        when(jdbc.queryForObject(PostgresAtomicStockStore.LOCK, Integer.class, 1L))
                .thenThrow(new EmptyResultDataAccessException(1));
        failure(Reason.STOCK_NOT_INITIALIZED, before -> { fail("Planner must not run"); return null; });
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void locksThenPlansOnceAndMapsAllPersistedMetadata() throws Exception {
        existing();
        when(jdbc.update(PostgresAtomicStockStore.UPDATE, 3, now.atOffset(ZoneOffset.UTC), 1L)).thenReturn(1);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(12L);
        when(rs.getLong("product_id")).thenReturn(1L);
        when(rs.getString("movement_type")).thenReturn("SALE_DEDUCT");
        when(rs.getInt("quantity_change")).thenReturn(-2);
        when(rs.getInt("quantity_after")).thenReturn(3);
        when(rs.getString("reference_type")).thenReturn("SALE");
        when(rs.getObject("reference_id", Long.class)).thenReturn(7L);
        when(rs.getString("reason")).thenReturn("Checkout");
        when(rs.getLong("created_by")).thenReturn(2L);
        when(rs.getObject("created_at", OffsetDateTime.class)).thenReturn(now.atOffset(ZoneOffset.UTC));
        when(jdbc.queryForObject(eq(PostgresAtomicStockStore.INSERT), any(RowMapper.class),
                eq(1L), eq("SALE_DEDUCT"), eq(-2), eq(3), eq("SALE"), eq(7L), eq("Checkout"), eq(2L), eq(now.atOffset(ZoneOffset.UTC))))
                .thenAnswer(call -> ((RowMapper<?>) call.getArgument(1)).mapRow(rs, 0));
        AtomicInteger calls = new AtomicInteger();
        StockMovementView result = store.apply(1L, before -> {
            verify(jdbc).queryForObject(PostgresAtomicStockStore.LOCK, Integer.class, 1L);
            assertEquals(5, before);
            calls.incrementAndGet();
            return plan(before);
        });
        assertEquals(1, calls.get());
        assertEquals(new StockMovementView(12L, 1L, MovementKind.SALE_DEDUCT, -2, 3, context, now), result);
        verify(manager).commit(status);
        verify(manager).getTransaction(argThat(def -> def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRED && !def.isReadOnly()));
    }

    @Test void nullPlanRollsBackBeforeWrites() {
        existing(); failure(Reason.INVALID_WRITE_PLAN, before -> null);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
    @Test void wrongProductRollsBack() {
        existing(); failure(Reason.INVALID_WRITE_PLAN, before -> new MovementWrite(2L, MovementKind.SALE_DEDUCT, -2, 3, context, now));
    }
    @Test void inconsistentAfterBalanceRollsBack() {
        existing(); failure(Reason.INVALID_WRITE_PLAN, before -> new MovementWrite(1L, MovementKind.SALE_DEDUCT, -2, 4, context, now));
    }
    @Test void overflowIsRejectedBeforeWrites() {
        existing();
        when(jdbc.queryForObject(PostgresAtomicStockStore.LOCK, Integer.class, 1L)).thenReturn(Integer.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> store.apply(1L,
                before -> new MovementWrite(1L, MovementKind.PURCHASE_RECEIVE, 1, 1, context, now)));
        verify(manager).rollback(status);
    }
    @Test void unexpectedUpdateCountRollsBack() {
        existing();
        when(jdbc.update(PostgresAtomicStockStore.UPDATE, 3, now.atOffset(ZoneOffset.UTC), 1L)).thenReturn(2);
        failure(Reason.UNEXPECTED_ROW_COUNT, this::plan);
    }
    @Test void zeroUpdatedRowsCannotReportSuccess() {
        existing();
        when(jdbc.update(PostgresAtomicStockStore.UPDATE, 3, now.atOffset(ZoneOffset.UTC), 1L)).thenReturn(0);
        failure(Reason.UNEXPECTED_ROW_COUNT, this::plan);
    }
    @Test void plannerFailurePropagatesBeforeWrites() {
        existing();
        IllegalArgumentException error = new IllegalArgumentException("Rejected policy");
        assertSame(error, assertThrows(IllegalArgumentException.class,
                () -> store.apply(1L, before -> { throw error; })));
        verify(manager).rollback(status);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
    @Test void oversizedReferenceTypeRejectsBeforeWrites() {
        existing();
        MovementContext oversized = new MovementContext("X".repeat(65), 7L, "Checkout", context.actor());
        assertThrows(IllegalArgumentException.class, () -> store.apply(1L,
                before -> new MovementWrite(1L, MovementKind.SALE_DEDUCT, -2, 3, oversized, now)));
        verify(manager).rollback(status);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
    @Test void commitFailureCannotReturnSuccess() {
        existing();
        when(jdbc.update(PostgresAtomicStockStore.UPDATE, 3, now.atOffset(ZoneOffset.UTC), 1L)).thenReturn(1);
        when(jdbc.queryForObject(eq(PostgresAtomicStockStore.INSERT), any(RowMapper.class), any(Object[].class)))
                .thenReturn(new StockMovementView(12L, 1L, MovementKind.SALE_DEDUCT, -2, 3, context, now));
        TransactionSystemException error = new TransactionSystemException("Commit failed");
        doThrow(error).when(manager).commit(status);
        assertSame(error, assertThrows(TransactionSystemException.class, () -> store.apply(1L, this::plan)));
        // Outcome may be ambiguous in a real commit failure; do not assert rollback or retry.
    }
    @Test void writeErrorPropagatesAndRequestsRollback() {
        existing();
        when(jdbc.update(PostgresAtomicStockStore.UPDATE, 3, now.atOffset(ZoneOffset.UTC), 1L)).thenReturn(1);
        DataAccessException error = new DataIntegrityViolationException("simulated insert failure");
        when(jdbc.queryForObject(eq(PostgresAtomicStockStore.INSERT), any(RowMapper.class),
                any(Object[].class))).thenThrow(error);
        assertSame(error, assertThrows(DataAccessException.class, () -> store.apply(1L, this::plan)));
        verify(manager).rollback(status);
        verify(manager, never()).commit(any());
    }
    @Test void readOnlyTransactionIsRejectedBeforeSql() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        failure(Reason.READ_ONLY, this::plan);
        verifyNoInteractions(jdbc);
    }
    @Test void inputValidationDoesNotStartTransaction() {
        assertThrows(IllegalArgumentException.class, () -> store.apply(0L, this::plan));
        assertThrows(IllegalArgumentException.class, () -> store.apply(null, this::plan));
        assertThrows(NullPointerException.class, () -> store.apply(1L, null));
        verifyNoInteractions(manager, jdbc);
    }
    @Test void suppliedTemplateIsNotMutated() {
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setReadOnly(true);
        new PostgresAtomicStockStore(jdbc, template);
        assertTrue(template.isReadOnly());
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW, template.getPropagationBehavior());
    }
}
