package com.bajaj.inventory_backend.inventory.persistence.jdbc;

import com.bajaj.inventory_backend.inventory.domain.MovementKind;
import com.bajaj.inventory_backend.inventory.dto.InventoryContracts.CurrentStockQuery;
import com.bajaj.inventory_backend.inventory.dto.InventoryContracts.PageQuery;
import com.bajaj.inventory_backend.inventory.dto.InventoryContracts.StockMovementQuery;
import com.bajaj.inventory_backend.inventory.persistence.StockPersistenceException;
import com.bajaj.inventory_backend.inventory.persistence.StockPersistenceException.Reason;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PostgresInventoryQueriesTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PostgresInventoryQueries queries = new PostgresInventoryQueries(jdbc);

    @Test
    void distinguishesMissingProductFromUninitializedStock() {
        when(jdbc.queryForObject(contains("LEFT JOIN inventory_stock"), any(RowMapper.class), eq(1L)))
                .thenThrow(new EmptyResultDataAccessException(1));
        when(jdbc.queryForObject(contains("LEFT JOIN inventory_stock"), any(RowMapper.class), eq(2L)))
                .thenAnswer(call -> call.<RowMapper<?>>getArgument(1).mapRow(mock(ResultSet.class), 0));

        assertEquals(Reason.PRODUCT_NOT_FOUND,
                assertThrows(StockPersistenceException.class, () -> queries.getCurrentStock(1L)).reason());
        assertEquals(Reason.STOCK_NOT_INITIALIZED,
                assertThrows(StockPersistenceException.class, () -> queries.getCurrentStock(2L)).reason());
    }

    @Test
    void mapsInitializedZeroAndPreservesUnknownThreshold() throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getLong("id")).thenReturn(1L);
        when(row.getLong("category_id")).thenReturn(2L);
        when(row.getString("product_code")).thenReturn("P1");
        when(row.getString("product_name")).thenReturn("Part");
        when(row.getObject("quantity", Integer.class)).thenReturn(0);
        when(jdbc.queryForObject(contains("LEFT JOIN inventory_stock"), any(RowMapper.class), eq(1L)))
                .thenAnswer(call -> call.<RowMapper<?>>getArgument(1).mapRow(row, 0));

        var stock = queries.getCurrentStock(1L);
        assertEquals(0, stock.quantity());
        assertNull(stock.reorderLevel());
    }

    @Test
    void bindsStockFiltersAndLiteralCaseInsensitiveSearchWithStablePagination() {
        String from = " FROM products p JOIN inventory_stock s ON s.product_id = p.id WHERE 1=1"
                + " AND p.id = ? AND p.category_id = ? AND p.active = ?"
                + " AND (p.product_code ILIKE ? ESCAPE '!' OR p.product_name ILIKE ? ESCAPE '!')"
                + " AND s.reorder_level IS NOT NULL AND s.quantity <= s.reorder_level";
        String pattern = "%Br!%!_!!%";
        when(jdbc.queryForObject("SELECT count(*)" + from, Long.class, 1L, 2L, false, pattern, pattern))
                .thenReturn(0L);
        when(jdbc.query(endsWith(from + " ORDER BY p.product_code ASC, p.id ASC LIMIT ? OFFSET ?"),
                any(RowMapper.class), eq(1L), eq(2L), eq(false), eq(pattern), eq(pattern), eq(10), eq(20L)))
                .thenReturn(List.of());

        var result = queries.searchCurrentStock(1L, new CurrentStockQuery(" Br%_! ", 2L, false, true),
                new PageQuery(2, 10));
        assertTrue(result.isEmpty());
        assertEquals(0L, result.getTotalElements());
        assertEquals(2, result.getNumber());
        verify(jdbc).query(endsWith(from + " ORDER BY p.product_code ASC, p.id ASC LIMIT ? OFFSET ?"),
                any(RowMapper.class), eq(1L), eq(2L), eq(false), eq(pattern), eq(pattern), eq(10), eq(20L));
    }

    @Test
    void bindsMovementHistoryFiltersAndMapsTheLedgerRow() throws Exception {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = start.plusSeconds(60);
        var fromTime = start.atOffset(ZoneOffset.UTC);
        var toTime = end.atOffset(ZoneOffset.UTC);
        String from = " FROM stock_movements WHERE 1=1 AND product_id = ? AND movement_type = ?"
                + " AND reference_type = ? AND reference_id = ? AND created_by = ?"
                + " AND created_at >= ? AND created_at < ?";
        when(jdbc.queryForObject("SELECT count(*)" + from, Long.class,
                1L, "SALE_DEDUCT", "SALE", 3L, 4L, fromTime, toTime)).thenReturn(1L);
        ResultSet row = mock(ResultSet.class);
        when(row.getLong("id")).thenReturn(7L);
        when(row.getLong("product_id")).thenReturn(1L);
        when(row.getString("movement_type")).thenReturn("SALE_DEDUCT");
        when(row.getInt("quantity_change")).thenReturn(-1);
        when(row.getString("reference_type")).thenReturn("SALE");
        when(row.getObject("reference_id", Long.class)).thenReturn(3L);
        when(row.getString("reason")).thenReturn("Checkout");
        when(row.getLong("created_by")).thenReturn(4L);
        when(row.getObject("created_at", java.time.OffsetDateTime.class)).thenReturn(fromTime);
        when(jdbc.query(endsWith(from + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?"),
                any(RowMapper.class), eq(1L), eq("SALE_DEDUCT"), eq("SALE"), eq(3L), eq(4L),
                eq(fromTime), eq(toTime), eq(10), eq(0L)))
                .thenAnswer(call -> List.of(call.<RowMapper<?>>getArgument(1).mapRow(row, 0)));

        var result = queries.searchStockMovements(
                new StockMovementQuery(1L, MovementKind.SALE_DEDUCT, "SALE", 3L, 4L, start, end),
                new PageQuery(0, 10));
        var movement = result.getContent().getFirst();
        assertEquals(1L, result.getTotalElements());
        assertEquals(7L, movement.movementId());
        assertEquals(MovementKind.SALE_DEDUCT, movement.movementType());
        assertEquals(-1, movement.quantityChange());
        assertEquals(0, movement.quantityAfter());
        assertEquals(3L, movement.context().referenceId());
        assertEquals(4L, movement.context().actor().userId());
        assertEquals(start, movement.createdAt());
    }

    @Test
    void rejectsInvalidPaginationAndProductFilterBeforeSql() {
        assertThrows(IllegalArgumentException.class, () -> new PageQuery(-1, 10));
        assertThrows(IllegalArgumentException.class, () -> new PageQuery(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PageQuery(0, 101));
        assertThrows(IllegalArgumentException.class, () -> queries.searchCurrentStock(
                0L, new CurrentStockQuery(null, null, null, false), new PageQuery(0, 100)));
        verifyNoInteractions(jdbc);
    }
}
