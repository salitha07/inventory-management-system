package com.bajaj.inventory_backend.inventory.service;

import com.bajaj.inventory_backend.category.Category;
import com.bajaj.inventory_backend.common.exception.InsufficientStockException;
import com.bajaj.inventory_backend.entity.Role;
import com.bajaj.inventory_backend.entity.User;
import com.bajaj.inventory_backend.inventory.core.InventoryOperations;
import com.bajaj.inventory_backend.inventory.core.InventoryPorts.AtomicStockStore;
import com.bajaj.inventory_backend.inventory.core.InventoryPorts.OperationPolicy;
import com.bajaj.inventory_backend.inventory.core.InventoryQueries;
import com.bajaj.inventory_backend.inventory.domain.MovementKind;
import com.bajaj.inventory_backend.inventory.dto.InventoryContracts.*;
import com.bajaj.inventory_backend.inventory.persistence.jpa.JpaStockInitializer;
import com.bajaj.inventory_backend.product.Product;
import com.bajaj.inventory_backend.product.dto.ProductPriceDetails;
import com.bajaj.inventory_backend.product.service.ProductCatalogService;
import com.bajaj.inventory_backend.repository.UserRepository;
import com.bajaj.inventory_backend.sales.PaymentMethod;
import com.bajaj.inventory_backend.sales.Sale;
import com.bajaj.inventory_backend.sales.SaleItem;
import com.bajaj.inventory_backend.sales.SalesRepository;
import com.bajaj.inventory_backend.sales.SalesService;
import com.bajaj.inventory_backend.sales.dto.SaleCreateRequest;
import com.bajaj.inventory_backend.sales.dto.SaleItemRequest;
import com.bajaj.inventory_backend.sales.dto.SaleResponse;
import com.bajaj.inventory_backend.sales.validation.CheckoutValidator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

/** Uses only the disposable loopback database described in the Inventory contract. */
@EnabledIfEnvironmentVariable(named = "INVENTORY_TEST_POSTGRES_PORT", matches = "[0-9]+")
class SalesCheckoutPostgresTest {
    private static final BigDecimal TEST_COST = new BigDecimal("12.34");
    private static final BigDecimal TEST_PRICE = new BigDecimal("25.00");
    private static final String TEST_REASON = "Checkout integration fixture";
    private static AnnotationConfigApplicationContext context;

    private SalesService sales;
    private InventoryQueries queries;
    private JpaStockInitializer initializer;
    private InventoryOperations operations;
    private CheckoutFixture fixture;
    private TransactionTemplate tx;
    private EntityManager em;
    private UserDetails principal;
    private long actorId;

    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = {JpaStockInitializer.class, SalesRepository.class, UserRepository.class},
            excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                    pattern = "com\\.bajaj\\.inventory_backend\\.repository\\.(?!UserRepository$).*"))
    @ComponentScan(basePackageClasses = JpaStockInitializer.class, useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                    pattern = ".*\\.(JpaAtomicStockStore|JpaInventoryQueries|JpaStockInitializer)$"))
    @Import({SalesService.class, SalesStockActorResolver.class})
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
            String inventoryPackage = JpaStockInitializer.class.getPackageName();
            factory.setManagedTypes(PersistenceManagedTypes.of(inventoryPackage + ".InventoryStock",
                    inventoryPackage + ".StockMovement", Product.class.getName(), Category.class.getName(),
                    Sale.class.getName(), SaleItem.class.getName(), User.class.getName(), Role.class.getName()));
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate"));
            return factory;
        }

        @Bean
        JpaTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }

        @Bean
        CheckoutFixture checkoutFixture() {
            return new CheckoutFixture();
        }

        @Bean
        InventoryOperations operations(AtomicStockStore store, SalesStockActorResolver actors) {
            return new InventoryOperations(store, new OperationPolicy() {
                @Override
                public void requireTrustedActor(AuthenticatedActor actor) {
                    if (!actors.requireCheckoutActor().equals(actor)) {
                        throw new AccessDeniedException("Fixture actor mismatch");
                    }
                }

                @Override
                public void requireApprovedRestockableReturn(StockChangeCommand command) {
                    throw new AccessDeniedException("Returns are outside this checkout fixture");
                }
            }, Clock.systemUTC());
        }

        @Bean
        InventoryService inventoryService(InventoryQueries queries, InventoryOperations operations,
                                          SalesStockActorResolver actors, CheckoutFixture fixture) {
            return new SalesInventoryService(queries, operations, new SalesInventoryService.SalesContext() {
                @Override
                public MovementContext deductionContext(Long saleId) {
                    fixture.beforeDeduction.run();
                    return new MovementContext("SALE", saleId, TEST_REASON, actors.requireCheckoutActor());
                }

                @Override
                public BigDecimal currentUnitCost(Long productId) {
                    return TEST_COST;
                }
            }, actors);
        }

        @Bean
        ProductCatalogService catalog(EntityManagerFactory factory) {
            var em = SharedEntityManagerCreator.createSharedEntityManager(factory);
            return new ProductCatalogService() {
                @Override
                public ProductPriceDetails getProductDetails(Long productId) {
                    var product = em.find(Product.class, productId);
                    return new ProductPriceDetails(product.getId(), product.getProductCode(),
                            product.getProductName(), product.getSellingPrice(), product.isActive());
                }

                @Override
                public BigDecimal getAuthoritativeSellingPrice(Long productId) {
                    return getProductDetails(productId).sellingPrice();
                }
            };
        }
    }

    /** Calls the real preflight before coordinating competing checkout transactions. */
    static class CheckoutFixture extends CheckoutValidator {
        private volatile Runnable afterAvailability = () -> { };
        private volatile Runnable beforeDeduction = () -> { };

        @Override
        public void validateStockAvailability(List<SaleItemRequest> items, InventoryService inventory) {
            super.validateStockAvailability(items, inventory);
            afterAvailability.run();
        }

        void reset() {
            afterAvailability = () -> { };
            beforeDeduction = () -> { };
        }
    }

    @BeforeAll
    static void startContext() {
        context = new AnnotationConfigApplicationContext(Config.class);
    }

    @AfterAll
    static void stopContext() {
        if (context != null) context.close();
    }

    @BeforeEach
    void setup() {
        sales = context.getBean(SalesService.class);
        assertTrue(AopUtils.isAopProxy(sales), "Checkout must start its real Spring transaction");
        queries = context.getBean(InventoryQueries.class);
        initializer = context.getBean(JpaStockInitializer.class);
        operations = context.getBean(InventoryOperations.class);
        fixture = context.getBean(CheckoutFixture.class);
        tx = new TransactionTemplate(context.getBean(JpaTransactionManager.class));
        em = SharedEntityManagerCreator.createSharedEntityManager(context.getBean(EntityManagerFactory.class));

        String email = UUID.randomUUID() + "@example.test";
        actorId = tx.execute(status -> ((Number) em.createNativeQuery("""
                INSERT INTO users(full_name,email,password_hash,role_id)
                VALUES ('Checkout fixture', :email, 'fixture', (SELECT id FROM roles WHERE name = 'CASHIER'))
                RETURNING id
                """).setParameter("email", email).getSingleResult()).longValue());
        principal = org.springframework.security.core.userdetails.User.withUsername(email)
                .password("fixture").roles("CASHIER").build();
    }

    @AfterEach
    void resetFixture() {
        fixture.reset();
    }

    @Test
    void commitsSaleItemsCostSnapshotAndStockDeduction() {
        long product = createStockedProduct(5);

        var sale = checkout(new SaleItemRequest(product, 2));

        assertCommittedCounts(1, 1);
        tx.executeWithoutResult(status -> {
            var persisted = em.find(Sale.class, sale.id());
            assertNotNull(persisted);
            assertEquals(actorId, persisted.getCreatedBy().getId());
            var item = persisted.getItems().getFirst();
            assertEquals(product, item.getProductId());
            assertEquals(2, item.getQuantity());
            assertEquals(TEST_COST, item.getUnitCostSnapshot());
            assertEquals(TEST_PRICE, item.getUnitSellingPrice());
        });
        assertEquals(3, queries.getCurrentStock(product).quantity());
    }

    @Test
    void recordsSaleDeductWithSavedSaleAndAuthenticatedActor() {
        long product = createStockedProduct(5);
        var sale = checkout(new SaleItemRequest(product, 2));

        var movement = deductions(product).getFirst();
        assertEquals(1, deductions(product).size());
        assertEquals(MovementKind.SALE_DEDUCT, movement.movementType());
        assertEquals(-2, movement.quantityChange());
        assertEquals(3, movement.quantityAfter());
        assertEquals(product, movement.productId());
        assertEquals("SALE", movement.context().referenceType());
        assertEquals(sale.id(), movement.context().referenceId());
        assertEquals(actorId, movement.context().actor().userId());
        assertEquals(TEST_REASON, movement.context().reason());
    }

    @Test
    void rejectsInsufficientStockWithoutCommittingAnyCheckoutWrites() {
        long product = createStockedProduct(1);
        var failure = assertThrows(InsufficientStockException.class, () -> checkout(new SaleItemRequest(product, 2)));
        assertEquals(product, failure.getProductId());
        assertEquals(1, failure.getAvailableQuantity());
        assertCommittedCounts(0, 0);
        assertEquals(1, queries.getCurrentStock(product).quantity());
        assertTrue(deductions(product).isEmpty());
    }

    @Test
    void twoCheckoutsThatPassPreflightCannotOversellTheLastUnit() throws Exception {
        long product = createStockedProduct(1);
        var preflight = new CyclicBarrier(2);
        fixture.afterAvailability = () -> await(preflight);
        Callable<Boolean> contender = () -> {
            try {
                checkout(new SaleItemRequest(product, 1));
                return true;
            } catch (InsufficientStockException expected) {
                assertEquals(0, expected.getAvailableQuantity());
                return false;
            }
        };
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = workers.submit(contender);
            var second = workers.submit(contender);
            assertNotEquals(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }
        assertCommittedCounts(1, 1);
        assertEquals(0, queries.getCurrentStock(product).quantity());
        assertEquals(1, deductions(product).size());
    }

    @Test
    void laterDeductionFailureRollsBackSaleItemsAndEarlierStockAndLedgerWrites() {
        long earlier = createStockedProduct(2);
        long later = createStockedProduct(2);
        var attempts = new AtomicInteger();
        fixture.beforeDeduction = () -> {
            if (attempts.incrementAndGet() == 2) {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                assertCounts(1, 2);
                assertEquals(1, queries.getCurrentStock(earlier).quantity());
                assertEquals(1, deductions(earlier).size());
                throw new InsufficientStockException(later, 1, 0);
            }
        };

        var failure = assertThrows(InsufficientStockException.class,
                () -> checkout(new SaleItemRequest(later, 1), new SaleItemRequest(earlier, 1)));
        assertEquals(later, failure.getProductId());
        assertEquals(2, attempts.get());

        tx.executeWithoutResult(status -> {
            assertCounts(0, 0);
            assertEquals(2, queries.getCurrentStock(earlier).quantity());
            assertEquals(2, queries.getCurrentStock(later).quantity());
            assertTrue(deductions(earlier).isEmpty());
            assertTrue(deductions(later).isEmpty());
        });
    }

    private SaleResponse checkout(SaleItemRequest... items) {
        return authenticated(() -> sales.processCheckout(
                new SaleCreateRequest(null, PaymentMethod.CASH, new BigDecimal("100.00"), null,
                        BigDecimal.ZERO, List.of(items)), SecurityContextHolder.getContext().getAuthentication()));
    }

    private long createStockedProduct(int quantity) {
        long product = tx.execute(status -> ((Number) em.createNativeQuery("""
                INSERT INTO products(product_code,product_name,category_id,cost_price,selling_price)
                VALUES (:code, 'Checkout fixture', (SELECT id FROM categories WHERE category_name = 'Inventory fixture'),
                        :cost, :price)
                RETURNING id
                """).setParameter("code", UUID.randomUUID().toString())
                .setParameter("cost", TEST_COST).setParameter("price", TEST_PRICE).getSingleResult()).longValue());
        initializer.initializeStock(product);
        authenticated(() -> operations.receiveStock(new StockChangeCommand(product, quantity,
                new MovementContext("FIXTURE", product, TEST_REASON, new AuthenticatedActor(actorId)))));
        return product;
    }

    private <T> T authenticated(java.util.function.Supplier<T> action) {
        var previous = SecurityContextHolder.getContext();
        var security = SecurityContextHolder.createEmptyContext();
        security.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(security);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private List<StockMovementView> deductions(long product) {
        return queries.searchStockMovements(new StockMovementQuery(product, MovementKind.SALE_DEDUCT,
                null, null, null, null, null), new PageQuery(0, 10)).getContent();
    }

    private void assertCommittedCounts(long sales, long items) {
        tx.executeWithoutResult(status -> assertCounts(sales, items));
    }

    private void assertCounts(long sales, long items) {
        assertEquals(sales, em.createQuery("SELECT count(s) FROM Sale s WHERE s.createdBy.id = :actor", Long.class)
                .setParameter("actor", actorId).getSingleResult());
        assertEquals(items, em.createQuery("SELECT count(i) FROM SaleItem i WHERE i.sale.createdBy.id = :actor", Long.class)
                .setParameter("actor", actorId).getSingleResult());
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Checkout preflight rendezvous was interrupted", ex);
        } catch (java.util.concurrent.BrokenBarrierException | java.util.concurrent.TimeoutException ex) {
            throw new AssertionError("Both checkouts must finish availability validation", ex);
        }
    }
}
