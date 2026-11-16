package com.bajaj.inventory_backend.inventory.persistence.jdbc;

import com.bajaj.inventory_backend.inventory.core.InventoryOperations;
import com.bajaj.inventory_backend.inventory.core.InventoryPorts.OperationPolicy;
import com.bajaj.inventory_backend.inventory.dto.InventoryContracts.*;
import com.bajaj.inventory_backend.inventory.persistence.StockPersistenceException;
import com.bajaj.inventory_backend.inventory.persistence.StockPersistenceException.Reason;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.*;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in adapter-level fixtures only. No Spring context, application DataSource or Flyway. */
@EnabledIfSystemProperty(named = "m6.pg.integration", matches = "true")
class PostgresAtomicStockStoreIT {
    private String schema;
    private boolean schemaCreated;
    private boolean workersStopped = true;
    private GuardedDataSource admin;
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private InventoryOperations operations;
    private final Instant now = Instant.parse("2026-11-16T12:00:00.123456Z");

    @BeforeEach void setUp() {
        String url = System.getenv("M6_PG_TEST_URL");
        String user = System.getenv("M6_PG_TEST_USER");
        String password = System.getenv("M6_PG_TEST_PASSWORD");
        Member6PostgresSafety.validate(url, user, password); // before creating any connection
        schema = "m6_it_" + UUID.randomUUID().toString().replace("-", "");
        Member6PostgresSafety.validateSchema(schema);
        admin = new GuardedDataSource(url, user, password, null);
        new JdbcTemplate(admin).execute("CREATE SCHEMA " + schema); // deliberately no IF NOT EXISTS
        schemaCreated = true;
        GuardedDataSource isolated = new GuardedDataSource(url, user, password, schema);
        new ResourceDatabasePopulator(new ClassPathResource("inventory/member6-adapter-fixture.sql")).execute(isolated);
        jdbc = new JdbcTemplate(isolated);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(isolated));
        transactions.setTimeout(15);
        operations = new InventoryOperations(new PostgresAtomicStockStore(jdbc, transactions), new FixturePolicy(), Clock.fixed(now, ZoneOffset.UTC));
        jdbc.update("INSERT INTO users(id) VALUES (2)");
        jdbc.update("INSERT INTO products(id) VALUES (1), (3)");
        jdbc.update("INSERT INTO inventory_stock(product_id, quantity, reorder_level) VALUES (1, 5, 2)");
    }

    @AfterEach void cleanUp() {
        if (schemaCreated && workersStopped) {
            Member6PostgresSafety.validateSchema(schema);
            // Only this randomly generated schema created by this test is eligible for deletion.
            new JdbcTemplate(admin).execute("DROP SCHEMA " + schema + " CASCADE");
            schemaCreated = false;
        }
    }

    private MovementContext context(String type, Long actor) {
        return new MovementContext(type, 7L, "Fixture event", new AuthenticatedActor(actor));
    }
    private StockChangeCommand command(String type, int quantity) {
        return new StockChangeCommand(1L, quantity, context(type, 2L));
    }
    private int quantity() { return jdbc.queryForObject("SELECT quantity FROM inventory_stock WHERE product_id=1", Integer.class); }
    private int history() { return jdbc.queryForObject("SELECT count(*) FROM stock_movements", Integer.class); }

    @Test void receiptPersistsHistoryMetadataAndTimestamp() {
        StockMovementView result = operations.receiveStock(command("PURCHASE", 3));
        assertEquals(8, quantity());
        assertEquals(1, history());
        assertEquals(3, result.quantityChange());
        assertEquals(context("PURCHASE", 2L), result.context());
        assertEquals(now, result.createdAt());
        assertEquals("PURCHASE_RECEIVE", jdbc.queryForObject("SELECT movement_type FROM stock_movements WHERE id=?", String.class, result.movementId()));
        assertEquals(8, jdbc.queryForObject("SELECT quantity_after FROM stock_movements WHERE id=?", Integer.class, result.movementId()));
        assertEquals(now, jdbc.queryForObject("SELECT created_at FROM stock_movements WHERE id=?", OffsetDateTime.class, result.movementId()).toInstant());
    }

    @Test void deductionAndExactDepletionPersist() {
        assertEquals(3, operations.deductStock(command("SALE", 2)).quantityAfter());
        assertEquals(0, operations.deductStock(command("SALE", 3)).quantityAfter());
        assertEquals(0, quantity());
        assertEquals(2, history());
    }

    @Test void approvedReturnAndSignedManualAdjustmentPersist() {
        operations.returnStock(command("CUSTOMER_RETURN", 2));
        MovementContext manual = new MovementContext("MANUAL", null, "Fixture count", new AuthenticatedActor(2L));
        operations.manualAdjustment(new ManualAdjustmentCommand(1L, -3, manual));
        assertEquals(4, quantity());
        assertEquals(2, history());
        assertNull(jdbc.queryForObject("SELECT reference_id FROM stock_movements WHERE movement_type='MANUAL_ADJUSTMENT'", Long.class));
    }

    @Test void failedMovementInsertRollsBackBalance() {
        // Temporary fixture-only constraint forces failure after the balance UPDATE.
        jdbc.execute("ALTER TABLE stock_movements ADD CONSTRAINT fixture_reject_movement CHECK (quantity_change < 0)");
        assertThrows(DataIntegrityViolationException.class, () -> operations.receiveStock(command("PURCHASE", 2)));
        assertEquals(5, quantity());
        assertEquals(0, history());
    }

    @Test void callerRollbackUndoesBothWrites() {
        assertThrows(IllegalStateException.class, () -> transactions.execute(status -> {
            operations.receiveStock(command("PURCHASE", 2));
            assertEquals(7, quantity());
            assertEquals(1, history());
            throw new IllegalStateException("Fixture caller rejected");
        }));
        assertEquals(5, quantity());
        assertEquals(0, history());
    }

    @Test void twoIndependentTransactionsCannotOversell() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        workersStopped = false;
        CyclicBarrier start = new CyclicBarrier(2);
        Callable<Boolean> deduct = () -> {
            start.await(5, TimeUnit.SECONDS);
            try { operations.deductStock(command("SALE", 4)); return true; }
            catch (InventoryOperations.InsufficientStock ex) { return false; }
        };
        try {
            Future<Boolean> first = workers.submit(deduct);
            Future<Boolean> second = workers.submit(deduct);
            int successes = (first.get(20, TimeUnit.SECONDS) ? 1 : 0) + (second.get(20, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successes);
            assertEquals(1, quantity());
            assertEquals(1, history());
        } finally {
            workers.shutdownNow();
            workersStopped = workers.awaitTermination(20, TimeUnit.SECONDS);
            assertTrue(workersStopped, "Fixture workers did not stop; schema retained for manual investigation");
        }
    }

    @Test void missingProductAndUninitializedStockDiffer() {
        assertEquals(Reason.PRODUCT_NOT_FOUND, assertThrows(StockPersistenceException.class,
                () -> operations.receiveStock(new StockChangeCommand(99L, 1, context("PURCHASE", 2L)))).reason());
        assertEquals(Reason.STOCK_NOT_INITIALIZED, assertThrows(StockPersistenceException.class,
                () -> operations.receiveStock(new StockChangeCommand(3L, 1, context("PURCHASE", 2L)))).reason());
        assertEquals(0, history());
    }

    @Test void constraintsAndRejectedOperationsPreserveHistory() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE inventory_stock SET quantity=-1 WHERE product_id=1"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE inventory_stock SET reorder_level=-1 WHERE product_id=1"));
        assertThrows(InventoryOperations.InsufficientStock.class, () -> operations.deductStock(command("SALE", 6)));
        assertThrows(IllegalArgumentException.class, () -> operations.manualAdjustment(new ManualAdjustmentCommand(1L, -6, context("MANUAL", 2L))));
        assertThrows(SecurityException.class, () -> operations.receiveStock(new StockChangeCommand(1L, 1, context("PURCHASE", 9L))));
        assertEquals(5, quantity());
        assertEquals(0, history());
    }

    /** Fixture policy only; not proof of production authentication/return approval. */
    private static final class FixturePolicy implements OperationPolicy {
        public void requireTrustedActor(AuthenticatedActor actor) {
            if (actor.userId() != 2L) throw new SecurityException("Fixture actor rejected");
        }
        public void requireApprovedRestockableReturn(StockChangeCommand command) {
            if (!"CUSTOMER_RETURN".equals(command.context().referenceType())) throw new IllegalArgumentException("Fixture return not approved");
        }
    }

    /** Every connection verifies actual DB identity, then restricts search_path to owned schema. */
    private static final class GuardedDataSource extends DriverManagerDataSource {
        private final String schema;
        GuardedDataSource(String url, String user, String password, String schema) {
            super(url, user, password);
            this.schema = schema;
        }
        @Override public Connection getConnection() throws SQLException { return guard(super.getConnection()); }
        @Override public Connection getConnection(String user, String password) throws SQLException { return guard(super.getConnection(user, password)); }
        private Connection guard(Connection connection) throws SQLException {
            try {
                try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT current_database()")) {
                    if (!rs.next() || !Member6PostgresSafety.DATABASE.equals(rs.getString(1))) throw new SQLException("Unexpected test database");
                }
                if (schema != null) {
                    Member6PostgresSafety.validateSchema(schema);
                    connection.setSchema(schema); // no public-schema fallback
                    try (Statement stmt = connection.createStatement()) {
                        stmt.execute("SET lock_timeout = '5s'");
                        stmt.execute("SET statement_timeout = '15s'");
                    }
                }
                return connection;
            } catch (SQLException | RuntimeException ex) {
                connection.close();
                throw new SQLException("Member 6 test connection safety check failed");
            }
        }
    }
}
