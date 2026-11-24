package com.bajaj.inventory_backend.inventory.persistence.jpa;

import com.bajaj.inventory_backend.category.Category;
import com.bajaj.inventory_backend.product.Product;
import com.bajaj.inventory_backend.inventory.core.InventoryOperations;
import com.bajaj.inventory_backend.inventory.core.InventoryOperations.InsufficientStock;
import com.bajaj.inventory_backend.inventory.core.InventoryPorts.AtomicStockStore;
import com.bajaj.inventory_backend.inventory.core.InventoryPorts.MovementWrite;
import com.bajaj.inventory_backend.inventory.core.InventoryPorts.OperationPolicy;
import com.bajaj.inventory_backend.inventory.core.InventoryQueries;
import com.bajaj.inventory_backend.inventory.domain.MovementKind;
import com.bajaj.inventory_backend.inventory.dto.InventoryContracts.*;
import com.bajaj.inventory_backend.inventory.persistence.StockPersistenceException;
import com.bajaj.inventory_backend.inventory.persistence.StockPersistenceException.Reason;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.hibernate.query.NativeQuery;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Run only against the disposable loopback cluster described in the Inventory contract. */
@EnabledIfEnvironmentVariable(named = "INVENTORY_TEST_POSTGRES_PORT", matches = "[0-9]+")
class JpaInventoryPostgresTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static AnnotationConfigApplicationContext context;
    private InventoryQueries queries;
    private JpaStockInitializer initializer;
    private InventoryOperations operations;
    private EntityManager em;
    private TransactionTemplate tx;
    private long product;
    private long actor;

    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = InventoryStockRepository.class)
    @Import({JpaAtomicStockStore.class, JpaInventoryQueries.class, JpaStockInitializer.class})
    static class Config {
        @Bean
        DataSource dataSource() {
            int port = Integer.parseInt(System.getenv("INVENTORY_TEST_POSTGRES_PORT"));
            return new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:" + port
                    + "/inventory_jpa_test", "inventory_jpa_test", "");
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setManagedTypes(PersistenceManagedTypes.of(InventoryStock.class.getName(),
                    StockMovement.class.getName(), Product.class.getName(), Category.class.getName()));
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate"));
            return factory;
        }

        @Bean
        JpaTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
    }

    @BeforeAll
    static void openContext() {
        context = new AnnotationConfigApplicationContext(Config.class);
    }

    @AfterAll
    static void closeContext() {
        if (context != null) context.close();
    }

    @BeforeEach
    void setUp() {
        initializer = context.getBean(JpaStockInitializer.class);
        queries = context.getBean(InventoryQueries.class);
        operations = new InventoryOperations(context.getBean(AtomicStockStore.class),
                mock(OperationPolicy.class), Clock.fixed(NOW, ZoneOffset.UTC));
        em = SharedEntityManagerCreator.createSharedEntityManager(context.getBean(EntityManagerFactory.class));
        tx = new TransactionTemplate(context.getBean(JpaTransactionManager.class));
        String token = UUID.randomUUID().toString();
        tx.executeWithoutResult(status -> {
            actor = insertId("""
                    INSERT INTO users(full_name,email,password_hash,role_id)
                    VALUES ('Inventory fixture', :token, 'fixture', (SELECT id FROM roles WHERE name = 'ADMIN'))
                    RETURNING id
                    """, token);
            product = insertId("""
                    INSERT INTO products(product_code,product_name,category_id,cost_price,selling_price)
                    VALUES (:token, 'Bracket %_!', (SELECT id FROM categories WHERE category_name = 'Inventory fixture'), 1, 2)
                    RETURNING id
                    """, token);
        });
    }

    @Test
    void initializationHandlesMissingAndConcurrentProductsAndPreservesExistingData() throws Exception {
        assertReason(Reason.PRODUCT_NOT_FOUND, () -> initializer.initializeStock(Long.MAX_VALUE));
        assertReason(Reason.PRODUCT_NOT_FOUND, () -> queries.getCurrentStock(Long.MAX_VALUE));
        assertReason(Reason.STOCK_NOT_INITIALIZED, () -> queries.getCurrentStock(product));
        assertEquals(List.of("ALREADY_INITIALIZED", "INITIALIZED"), race(
                () -> initializer.initializeStock(product).name()).stream().sorted().toList());
        assertEquals(0, queries.getCurrentStock(product).quantity());
        assertNull(queries.getCurrentStock(product).reorderLevel());
        assertEquals(1, queries.searchCurrentStock(product, new CurrentStockQuery(null, null, null, false),
                new PageQuery(0, 10)).getTotalElements());
        setThreshold(2);
        operations.receiveStock(change(2, actor));
        assertEquals(JpaStockInitializer.Result.ALREADY_INITIALIZED, initializer.initializeStock(product));
        assertEquals(2, queries.getCurrentStock(product).quantity());
        assertEquals(2, queries.getCurrentStock(product).reorderLevel());
        assertEquals(1, history(null).getTotalElements());
    }

    @Test
    void searchesLiteralTextAndPaginatesStockAndHistoryWithStableOrdering() {
        initializer.initializeStock(product);
        var low = new CurrentStockQuery("bRaCkEt %_!", null, true, true);
        assertTrue(queries.searchCurrentStock(product, low, new PageQuery(0, 1)).isEmpty());
        setThreshold(0);
        var page = queries.searchCurrentStock(product, low, new PageQuery(0, 1));
        assertEquals(product, page.getContent().getFirst().productId());
        assertEquals(1, page.getTotalElements());
        assertTrue(queries.searchCurrentStock(product, low, new PageQuery(1, 1)).isEmpty());
        var movement = operations.receiveStock(change(2, actor));
        operations.deductStock(change(1, actor));
        operations.returnStock(change(1, actor));
        operations.manualAdjustment(new ManualAdjustmentCommand(product, -2,
                new MovementContext("ADJUSTMENT", null, "Fixture", new AuthenticatedActor(actor))));
        assertEquals(0, queries.getCurrentStock(product).quantity());
        assertEquals(List.of(MovementKind.MANUAL_ADJUSTMENT, MovementKind.CUSTOMER_RETURN,
                MovementKind.SALE_DEDUCT, MovementKind.PURCHASE_RECEIVE),
                history(null).getContent().stream().map(StockMovementView::movementType).toList());
        var filter = new StockMovementQuery(product, MovementKind.PURCHASE_RECEIVE,
                "FIXTURE", 1L, actor, NOW, NOW.plusSeconds(1));
        assertEquals(List.of(movement), queries.searchStockMovements(filter, new PageQuery(0, 1)).getContent());
        assertTrue(queries.searchStockMovements(filter, new PageQuery(1, 1)).isEmpty());
    }

    @Test
    void concurrentDeductionsCannotOversell() throws Exception {
        initializer.initializeStock(product);
        operations.receiveStock(change(1, actor));
        var results = race(() -> {
            try {
                operations.deductStock(change(1, actor));
                return true;
            } catch (InsufficientStock ex) {
                return false;
            }
        });
        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(0, queries.getCurrentStock(product).quantity());
        assertEquals(1, history(MovementKind.SALE_DEDUCT).getTotalElements());
    }

    @Test
    void sharedCallerRollbackAndLedgerFailureLeaveBothWritesUnchanged() {
        initializer.initializeStock(product);
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
            em.find(Product.class, product).setProductName("Rolled-back Product name");
            operations.receiveStock(change(3, actor));
            assertEquals(3, queries.getCurrentStock(product).quantity());
            throw new IllegalStateException("Caller abort");
        }));
        assertThrows(RuntimeException.class, () -> operations.receiveStock(change(3, Long.MAX_VALUE)));
        assertEquals(0, queries.getCurrentStock(product).quantity());
        assertEquals("Bracket %_!", queries.getCurrentStock(product).productName());
        assertTrue(history(null).isEmpty());
    }

    @Test
    void refreshesTheLockedBalanceAlreadyManagedByTheCaller() {
        initializer.initializeStock(product);
        operations.receiveStock(change(1, actor));
        var independent = new TransactionTemplate(tx.getTransactionManager());
        independent.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThrows(InsufficientStock.class, () -> tx.executeWithoutResult(status -> {
            assertEquals(1, em.find(InventoryStock.class, product).quantity());
            independent.executeWithoutResult(other -> operations.deductStock(change(1, actor)));
            operations.deductStock(change(1, actor));
        }));
        assertEquals(0, queries.getCurrentStock(product).quantity());
        assertEquals(1, history(MovementKind.SALE_DEDUCT).getTotalElements());
    }

    @Test
    void rejectsMissingBalanceAndReadOnlyMutationsWithoutCreatingLedgerRows() {
        assertReason(Reason.STOCK_NOT_INITIALIZED, () -> operations.receiveStock(change(1, actor)));
        var readOnly = new TransactionTemplate(tx.getTransactionManager());
        readOnly.setReadOnly(true);
        assertReason(Reason.READ_ONLY, () -> readOnly.execute(status -> initializer.initializeStock(product)));
        assertReason(Reason.STOCK_NOT_INITIALIZED, () -> queries.getCurrentStock(product));

        initializer.initializeStock(product);
        assertReason(Reason.READ_ONLY, () -> readOnly.execute(status -> operations.receiveStock(change(1, actor))));
        assertEquals(0, queries.getCurrentStock(product).quantity());
        assertTrue(history(null).isEmpty());
    }

    @Test
    void rejectsAnOversizedReferenceWithoutChangingTheBalance() {
        initializer.initializeStock(product);
        var context = new MovementContext("X".repeat(65), 1L, "Fixture", new AuthenticatedActor(actor));
        assertThrows(IllegalArgumentException.class,
                () -> operations.receiveStock(new StockChangeCommand(product, 1, context)));
        assertEquals(0, queries.getCurrentStock(product).quantity());
        assertTrue(history(null).isEmpty());
    }

    @Test
    void databaseRejectsNegativeStockAndInvalidLedgerRows() {
        initializer.initializeStock(product);
        assertCheckViolation(() -> tx.executeWithoutResult(status -> em.createNativeQuery("""
                UPDATE inventory_stock SET quantity = -1 WHERE product_id = :id
                """).setParameter("id", product).executeUpdate()));
        assertCheckViolation(() -> setThreshold(-1));
        assertCheckViolation(() -> insertLedger("UNKNOWN", 1, 1, 1L));
        assertCheckViolation(() -> insertLedger("SALE_DEDUCT", 1, 1, 1L));
        assertCheckViolation(() -> insertLedger("MANUAL_ADJUSTMENT", 0, 0, null));
        assertCheckViolation(() -> insertLedger("PURCHASE_RECEIVE", 1, 1, null));
        assertCheckViolation(() -> insertLedger("PURCHASE_RECEIVE", 2, 1, 1L));
        assertCheckViolation(() -> insertLedger("SALE_DEDUCT", -1, -1, 1L));
        assertEquals(0, queries.getCurrentStock(product).quantity());
        assertNull(queries.getCurrentStock(product).reorderLevel());
        assertTrue(history(null).isEmpty());
    }

    private void insertLedger(String kind, int change, int after, Long referenceId) {
        tx.executeWithoutResult(status -> em.createNativeQuery("""
                INSERT INTO stock_movements
                (product_id, movement_type, quantity_change, quantity_after, reference_type, reference_id, reason, created_by)
                VALUES (:product, :kind, :change, :after, 'FIXTURE', :reference, 'Test', :actor)
                """).unwrap(NativeQuery.class)
                .setParameter("product", product).setParameter("kind", kind).setParameter("change", change)
                .setParameter("after", after).setParameter("reference", referenceId, Long.class)
                .setParameter("actor", actor).executeUpdate());
    }

    @Test
    void rejectsMalformedPlansWithoutChangingBalanceOrLedger() {
        initializer.initializeStock(product);
        var store = context.getBean(AtomicStockStore.class);
        var movementContext = change(1, actor).context();
        assertReason(Reason.INVALID_WRITE_PLAN, () -> store.apply(product, before -> null));
        assertReason(Reason.INVALID_WRITE_PLAN, () -> store.apply(product, before ->
                new MovementWrite(product + 1, MovementKind.PURCHASE_RECEIVE, 1, 1, movementContext, NOW)));
        assertReason(Reason.INVALID_WRITE_PLAN, () -> store.apply(product, before ->
                new MovementWrite(product, MovementKind.PURCHASE_RECEIVE, 1, 2, movementContext, NOW)));
        assertEquals(0, queries.getCurrentStock(product).quantity());
        assertTrue(history(null).isEmpty());
    }

    @Test
    void rollsBackFlushedStockAndLedgerWhenCallerCommitFails() {
        initializer.initializeStock(product);
        var failure = assertThrows(RuntimeException.class, () -> tx.executeWithoutResult(status -> {
            operations.receiveStock(change(3, actor));
            assertEquals(3, queries.getCurrentStock(product).quantity());
            assertEquals(1L, history(null).getTotalElements());
            em.createNativeQuery("""
                    CREATE TEMP TABLE inventory_deferred_check (
                        fixture_id BIGINT UNIQUE DEFERRABLE INITIALLY DEFERRED
                    ) ON COMMIT DROP
                    """).executeUpdate();
            em.createNativeQuery("INSERT INTO inventory_deferred_check VALUES (1), (1)").executeUpdate();
        }));
        assertSqlState("23505", failure);
        assertEquals(0, queries.getCurrentStock(product).quantity());
        assertTrue(history(null).isEmpty());
    }

    @Test
    void paginatesMultipleProductsInProductCodeOrder() {
        String prefix = "PAGE-" + UUID.randomUUID();
        var productIds = tx.execute(status -> List.of(
                insertPageProduct(prefix + "-C"), insertPageProduct(prefix + "-A"), insertPageProduct(prefix + "-B")));
        productIds.forEach(initializer::initializeStock);
        var search = new CurrentStockQuery(prefix, null, null, false);
        var first = queries.searchCurrentStock(null, search, new PageQuery(0, 2));
        var second = queries.searchCurrentStock(null, search, new PageQuery(1, 2));
        assertEquals(3L, first.getTotalElements());
        assertEquals(3L, second.getTotalElements());
        assertEquals(List.of(prefix + "-A", prefix + "-B"), first.map(CurrentStockView::productCode).getContent());
        assertEquals(List.of(prefix + "-C"), second.map(CurrentStockView::productCode).getContent());
    }

    private long insertPageProduct(String code) {
        return insertId("""
                INSERT INTO products(product_code,product_name,category_id,cost_price,selling_price)
                VALUES (:token, 'Pagination fixture', (SELECT id FROM categories WHERE category_name = 'Inventory fixture'), 1, 2)
                RETURNING id
                """, code);
    }

    private static void assertCheckViolation(Runnable operation) {
        assertSqlState("23514", assertThrows(RuntimeException.class, operation::run));
    }

    private static void assertSqlState(String expected, Throwable error) {
        while (error != null && !(error instanceof SQLException)) {
            error = error.getCause();
        }
        assertInstanceOf(SQLException.class, error);
        assertEquals(expected, ((SQLException) error).getSQLState());
    }

    private void setThreshold(int threshold) {
        tx.executeWithoutResult(status -> em.createNativeQuery("""
                UPDATE inventory_stock SET reorder_level = :threshold WHERE product_id = :id
                """).setParameter("threshold", threshold).setParameter("id", product).executeUpdate());
    }

    private long insertId(String sql, String token) {
        return ((Number) em.createNativeQuery(sql).setParameter("token", token).getSingleResult()).longValue();
    }

    private StockChangeCommand change(int quantity, long actorId) {
        return new StockChangeCommand(product, quantity,
                new MovementContext("FIXTURE", 1L, "Test", new AuthenticatedActor(actorId)));
    }

    private org.springframework.data.domain.Page<StockMovementView> history(MovementKind kind) {
        return queries.searchStockMovements(new StockMovementQuery(product, kind, null, null, null, null, null),
                new PageQuery(0, 10));
    }

    private static void assertReason(Reason expected, Runnable operation) {
        assertEquals(expected, assertThrows(StockPersistenceException.class, operation::run).reason());
    }

    private static <T> List<T> race(Callable<T> operation) throws Exception {
        var barrier = new CyclicBarrier(2);
        Callable<T> contender = () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return operation.call();
        };
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = workers.submit(contender);
            var second = workers.submit(contender);
            return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        }
    }
}
