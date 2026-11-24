package com.bajaj.inventory_backend.inventory.persistence.jdbc;

import com.bajaj.inventory_backend.inventory.persistence.StockPersistenceException;
import com.bajaj.inventory_backend.inventory.persistence.StockPersistenceException.Reason;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Objects;

/**
 * Explicit Inventory-owned initialization, deliberately unwired until Product/V2 and V6 exist.
 * Inject JDBC and a template sharing the caller's DataSource and compatible transaction manager;
 * a separate JDBC manager does not coordinate with a Product/JPA transaction automatically.
 * Product creation should invoke this after persistence/flush in the same transaction.
 * Zero initialization is not a stock movement; nonzero opening stock requires an attributed
 * stock operation. Ordinary mutations must continue to reject uninitialized balances.
 */
public final class PostgresStockInitializer {
    public enum Result { INITIALIZED, ALREADY_INITIALIZED }

    private static final String PRODUCT = "SELECT id FROM products WHERE id = ? FOR KEY SHARE";
    private static final String INITIALIZE = """
            INSERT INTO inventory_stock (product_id, quantity, reorder_level)
            VALUES (?, 0, NULL)
            ON CONFLICT (product_id) DO NOTHING
            """;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public PostgresStockInitializer(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        Objects.requireNonNull(transactions, "transactions");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactions.getTransactionManager(), "transactionManager"), transactions);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.transactions.setReadOnly(false);
    }

    /**
     * The PK/ON CONFLICT arbitrates concurrent inserts without overwriting quantity,
     * reorder_level or updated_at. A joined transaction's result is provisional until commit.
     * Product key-share locking protects identity, not active status. Transaction/SQL failures
     * propagate; serialization failures or ambiguous commits are not automatically retried.
     * Product lifecycle wiring and active-status policy remain with the team.
     */
    public Result initializeStock(Long productId) {
        if (productId == null || productId <= 0) throw new IllegalArgumentException("productId must be positive");
        return transactions.execute(status -> {
            if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
                throw new StockPersistenceException(Reason.READ_ONLY);
            }
            try {
                if (!productId.equals(jdbc.queryForObject(PRODUCT, Long.class, productId))) {
                    throw new StockPersistenceException(Reason.PRODUCT_NOT_FOUND);
                }
            } catch (EmptyResultDataAccessException ex) {
                throw new StockPersistenceException(Reason.PRODUCT_NOT_FOUND);
            }
            return switch (jdbc.update(INITIALIZE, productId)) {
                case 1 -> Result.INITIALIZED;
                case 0 -> Result.ALREADY_INITIALIZED;
                default -> throw new StockPersistenceException(Reason.UNEXPECTED_ROW_COUNT);
            };
        });
    }
}
