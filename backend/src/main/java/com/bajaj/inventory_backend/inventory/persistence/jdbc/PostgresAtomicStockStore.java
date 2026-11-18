package com.bajaj.inventory_backend.inventory.persistence.jdbc;

import com.bajaj.inventory_backend.inventory.core.InventoryPorts.*;
import com.bajaj.inventory_backend.inventory.domain.MovementKind;
import com.bajaj.inventory_backend.inventory.dto.InventoryContracts.*;
import com.bajaj.inventory_backend.inventory.persistence.StockPersistenceException;
import com.bajaj.inventory_backend.inventory.persistence.StockPersistenceException.Reason;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.function.IntFunction;

/**
 * Unwired JDBC adapter: no component registration or startup SQL.
 * Supplied manager must coordinate this JdbcTemplate's DataSource with caller JPA/Sales.
 * A separate JDBC manager does not guarantee atomicity with a JPA caller.
 */
public final class PostgresAtomicStockStore implements AtomicStockStore {
    static final String PRODUCT = "SELECT id FROM products WHERE id = ? FOR KEY SHARE";
    static final String LOCK = "SELECT quantity FROM inventory_stock WHERE product_id = ? FOR UPDATE";
    static final String UPDATE = "UPDATE inventory_stock SET quantity = ?, updated_at = ? WHERE product_id = ?";
    static final String INSERT = """
            INSERT INTO stock_movements
            (product_id, movement_type, quantity_change, quantity_after, reference_type,
             reference_id, reason, created_by, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id, product_id, movement_type, quantity_change, quantity_after,
                      reference_type, reference_id, reason, created_by, created_at
            """;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public PostgresAtomicStockStore(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        Objects.requireNonNull(transactions, "transactions");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactions.getTransactionManager(), "transactionManager"), transactions);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.transactions.setReadOnly(false);
    }

    @Override
    public StockMovementView apply(Long productId, IntFunction<MovementWrite> planner) {
        if (productId == null || productId <= 0) throw new IllegalArgumentException("productId must be positive");
        Objects.requireNonNull(planner, "planner");
        return transactions.execute(status -> {
            if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
                throw new StockPersistenceException(Reason.READ_ONLY);
            }
            try {
                Long found = jdbc.queryForObject(PRODUCT, Long.class, productId);
                if (!productId.equals(found)) throw new StockPersistenceException(Reason.PRODUCT_NOT_FOUND);
            } catch (EmptyResultDataAccessException ex) {
                throw new StockPersistenceException(Reason.PRODUCT_NOT_FOUND);
            }
            Integer before;
            try {
                before = jdbc.queryForObject(LOCK, Integer.class, productId);
            } catch (EmptyResultDataAccessException ex) {
                throw new StockPersistenceException(Reason.STOCK_NOT_INITIALIZED);
            }
            if (before == null || before < 0) throw new StockPersistenceException(Reason.INVALID_WRITE_PLAN);
            MovementWrite write = planner.apply(before);
            if (write == null || !productId.equals(write.productId())
                    || write.kind().quantityAfter(before, write.change()) != write.quantityAfter()) {
                throw new StockPersistenceException(Reason.INVALID_WRITE_PLAN);
            }
            MovementContext context = write.context();
            if (context.referenceType().codePointCount(0, context.referenceType().length()) > 64) {
                throw new IllegalArgumentException("referenceType exceeds draft SQL length 64");
            }
            OffsetDateTime time = write.createdAt().atOffset(ZoneOffset.UTC);
            if (jdbc.update(UPDATE, write.quantityAfter(), time, productId) != 1) {
                throw new StockPersistenceException(Reason.UNEXPECTED_ROW_COUNT);
            }
            return Objects.requireNonNull(jdbc.queryForObject(INSERT, MOVEMENT_MAPPER,
                    productId, write.kind().name(), write.change(), write.quantityAfter(),
                    context.referenceType(), context.referenceId(), context.reason(), context.actor().userId(), time),
                    "Inserted movement missing");
        });
    }

    private static final RowMapper<StockMovementView> MOVEMENT_MAPPER = (rs, row) -> new StockMovementView(
            rs.getLong("id"), rs.getLong("product_id"), MovementKind.valueOf(rs.getString("movement_type")),
            rs.getInt("quantity_change"), rs.getInt("quantity_after"),
            new MovementContext(rs.getString("reference_type"), rs.getObject("reference_id", Long.class),
                    rs.getString("reason"), new AuthenticatedActor(rs.getLong("created_by"))),
            rs.getObject("created_at", OffsetDateTime.class).toInstant());
}
