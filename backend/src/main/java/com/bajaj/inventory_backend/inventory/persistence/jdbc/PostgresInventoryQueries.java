package com.bajaj.inventory_backend.inventory.persistence.jdbc;

import com.bajaj.inventory_backend.inventory.core.InventoryQueries;
import com.bajaj.inventory_backend.inventory.domain.MovementKind;
import com.bajaj.inventory_backend.inventory.dto.InventoryContracts.*;
import com.bajaj.inventory_backend.inventory.persistence.StockPersistenceException;
import com.bajaj.inventory_backend.inventory.persistence.StockPersistenceException.Reason;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Inventory read implementation, deliberately unwired until Product/V2 and V6 are available.
 * No schema creation, balance initialization, writes or Product entity dependency.
 * Count/content queries can observe concurrent changes unless the caller supplies a suitable
 * shared snapshot transaction. These reads do not reserve stock.
 */
public final class PostgresInventoryQueries implements InventoryQueries {
    private static final String STOCK_COLUMNS = "p.id, p.product_code, p.product_name, p.category_id, "
            + "p.active, s.quantity, s.reorder_level";
    private final JdbcTemplate jdbc;

    public PostgresInventoryQueries(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public CurrentStockView getCurrentStock(Long productId) {
        requireProductId(productId);
        try {
            return jdbc.queryForObject("SELECT " + STOCK_COLUMNS
                    + " FROM products p LEFT JOIN inventory_stock s ON s.product_id = p.id WHERE p.id = ?",
                    (rs, row) -> {
                        if (rs.getObject("quantity", Integer.class) == null) {
                            throw new StockPersistenceException(Reason.STOCK_NOT_INITIALIZED);
                        }
                        return STOCK_MAPPER.mapRow(rs, row);
                    }, productId);
        } catch (EmptyResultDataAccessException ex) {
            throw new StockPersistenceException(Reason.PRODUCT_NOT_FOUND);
        }
    }

    @Override
    public Page<CurrentStockView> searchCurrentStock(Long productId, CurrentStockQuery query, PageQuery page) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(page, "page");
        if (productId != null) requireProductId(productId);
        StringBuilder from = new StringBuilder(" FROM products p JOIN inventory_stock s ON s.product_id = p.id WHERE 1=1");
        List<Object> parameters = new ArrayList<>();
        filter(from, parameters, "p.id", productId);
        filter(from, parameters, "p.category_id", query.categoryId());
        filter(from, parameters, "p.active", query.active());
        if (query.text() != null && !query.text().isBlank()) {
            // Literal substring matching: caller '%' and '_' must not become SQL wildcards.
            String text = "%" + query.text().trim().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            from.append(" AND (p.product_code ILIKE ? ESCAPE '!' OR p.product_name ILIKE ? ESCAPE '!')");
            parameters.add(text);
            parameters.add(text);
        }
        if (query.lowStockOnly()) {
            from.append(" AND s.reorder_level IS NOT NULL AND s.quantity <= s.reorder_level");
        }
        return page("SELECT " + STOCK_COLUMNS, from.toString(), "p.product_code ASC, p.id ASC",
                parameters, page, STOCK_MAPPER);
    }

    @Override
    public Page<StockMovementView> searchStockMovements(StockMovementQuery query, PageQuery page) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(page, "page");
        StringBuilder from = new StringBuilder(" FROM stock_movements WHERE 1=1");
        List<Object> parameters = new ArrayList<>();
        filter(from, parameters, "product_id", query.productId());
        filter(from, parameters, "movement_type", query.movementType() == null ? null : query.movementType().name());
        filter(from, parameters, "reference_type", query.referenceType());
        filter(from, parameters, "reference_id", query.referenceId());
        filter(from, parameters, "created_by", query.createdBy());
        if (query.from() != null) {
            from.append(" AND created_at >= ?");
            parameters.add(query.from().atOffset(ZoneOffset.UTC));
        }
        if (query.to() != null) {
            from.append(" AND created_at < ?");
            parameters.add(query.to().atOffset(ZoneOffset.UTC));
        }
        return page("SELECT id, product_id, movement_type, quantity_change, quantity_after, "
                        + "reference_type, reference_id, reason, created_by, created_at",
                from.toString(), "created_at DESC, id DESC", parameters, page, MOVEMENT_MAPPER);
    }

    private <T> Page<T> page(String select, String from, String order, List<Object> parameters,
                              PageQuery page, RowMapper<T> mapper) {
        long total = Objects.requireNonNull(jdbc.queryForObject("SELECT count(*)" + from,
                Long.class, parameters.toArray()), "count");
        PageRequest request = PageRequest.of(page.page(), page.size());
        List<Object> paged = new ArrayList<>(parameters);
        paged.add(page.size());
        paged.add(request.getOffset());
        List<T> content = jdbc.query(select + from + " ORDER BY " + order + " LIMIT ? OFFSET ?",
                mapper, paged.toArray());
        return new PageImpl<>(content, request, total);
    }

    /** Column names are internal constants, never caller-controlled SQL. */
    private static void filter(StringBuilder sql, List<Object> parameters, String column, Object value) {
        if (value != null) {
            sql.append(" AND ").append(column).append(" = ?");
            parameters.add(value);
        }
    }

    private static void requireProductId(Long productId) {
        if (productId == null || productId <= 0) throw new IllegalArgumentException("productId must be positive");
    }

    private static final RowMapper<CurrentStockView> STOCK_MAPPER = (rs, row) -> new CurrentStockView(
            rs.getLong("id"), rs.getString("product_code"), rs.getString("product_name"),
            rs.getLong("category_id"), rs.getBoolean("active"), rs.getInt("quantity"),
            rs.getObject("reorder_level", Integer.class));

    private static final RowMapper<StockMovementView> MOVEMENT_MAPPER = (rs, row) -> new StockMovementView(
            rs.getLong("id"), rs.getLong("product_id"), MovementKind.valueOf(rs.getString("movement_type")),
            rs.getInt("quantity_change"), rs.getInt("quantity_after"),
            new MovementContext(rs.getString("reference_type"), rs.getObject("reference_id", Long.class),
                    rs.getString("reason"), new AuthenticatedActor(rs.getLong("created_by"))),
            rs.getObject("created_at", OffsetDateTime.class).toInstant());
}
