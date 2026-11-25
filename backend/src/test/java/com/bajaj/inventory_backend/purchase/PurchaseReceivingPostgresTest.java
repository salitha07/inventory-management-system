package com.bajaj.inventory_backend.purchase;

import com.bajaj.inventory_backend.category.Category;
import com.bajaj.inventory_backend.entity.Role;
import com.bajaj.inventory_backend.entity.User;
import com.bajaj.inventory_backend.entity.Supplier;
import com.bajaj.inventory_backend.product.Product;
import com.bajaj.inventory_backend.inventory.core.InventoryOperations;
import com.bajaj.inventory_backend.inventory.core.InventoryPorts.AtomicStockStore;
import com.bajaj.inventory_backend.inventory.core.InventoryPorts.OperationPolicy;
import com.bajaj.inventory_backend.inventory.core.InventoryQueries;
import com.bajaj.inventory_backend.inventory.domain.MovementKind;
import com.bajaj.inventory_backend.inventory.dto.InventoryContracts.*;
import com.bajaj.inventory_backend.inventory.persistence.jpa.JpaStockInitializer;
import com.bajaj.inventory_backend.repository.PurchaseRepository;
import com.bajaj.inventory_backend.repository.UserRepository;
import com.bajaj.inventory_backend.purchase.dto.CreatePurchaseRequest;
import com.bajaj.inventory_backend.purchase.dto.PurchaseItemRequest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import javax.sql.DataSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@EnabledIfEnvironmentVariable(
        named = "INVENTORY_TEST_POSTGRES_PORT",
        matches = "[0-9]+"
)
class PurchaseReceivingPostgresTest {

    private static AnnotationConfigApplicationContext context;

    private PurchaseService purchaseService;
    private PurchaseReceivingService receivingService;
    private InventoryQueries queries;
    private JpaStockInitializer initializer;
    private TransactionTemplate tx;
    private EntityManager em;

    private long actorId;
    private long supplierId;
    private UserDetails principal;

    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(
            basePackageClasses = {
                    JpaStockInitializer.class,
                    PurchaseRepository.class
            },
            excludeFilters = @ComponentScan.Filter(
                    type = FilterType.REGEX,
                    pattern = "com\\.bajaj\\.inventory_backend\\.repository\\.(?!PurchaseRepository$|SupplierRepository$|ProductRepository$|UserRepository$).*"
            )
    )
    @ComponentScan(
            basePackageClasses = JpaStockInitializer.class,
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.REGEX,
                    pattern = ".*\\.(JpaAtomicStockStore|JpaInventoryQueries|JpaStockInitializer)$"
            )
    )
    @Import({
            PurchaseService.class,
            PurchaseReceivingService.class
    })
    static class Config {

        @Bean
        DataSource dataSource() {

            int port = Integer.parseInt(
                    System.getenv("INVENTORY_TEST_POSTGRES_PORT")
            );

            if (port == 5432) {
                throw new IllegalStateException(
                        "Use a separate disposable PostgreSQL instance"
                );
            }

            return new DriverManagerDataSource(
                    "jdbc:postgresql://127.0.0.1:"
                            + port
                            + "/inventory_jpa_test",
                    "inventory_jpa_test",
                    ""
            );
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(
                DataSource dataSource
        ) {

            var factory =
                    new LocalContainerEntityManagerFactoryBean();

            factory.setDataSource(dataSource);

            factory.setJpaVendorAdapter(
                    new HibernateJpaVendorAdapter()
            );

            String inventoryPackage =
                    JpaStockInitializer.class.getPackageName();

            factory.setManagedTypes(
                    PersistenceManagedTypes.of(
                            inventoryPackage + ".InventoryStock",
                            inventoryPackage + ".StockMovement",
                            Product.class.getName(),
                            Category.class.getName(),
                            Supplier.class.getName(),
                            Purchase.class.getName(),
                            PurchaseItem.class.getName(),
                            User.class.getName(),
                            Role.class.getName()
                    )
            );

            factory.setJpaPropertyMap(
                    Map.of("hibernate.hbm2ddl.auto", "validate")
            );

            return factory;
        }

        @Bean
        JpaTransactionManager transactionManager(
                EntityManagerFactory factory
        ) {
            return new JpaTransactionManager(factory);
        }

        @Bean
        InventoryOperations inventoryOperations(
                AtomicStockStore store
        ) {

            return new InventoryOperations(
                    store,
                    mock(OperationPolicy.class),
                    Clock.systemUTC()
            );
        }
    }

    @BeforeAll
    static void startContext() {

        context = new AnnotationConfigApplicationContext(
                Config.class
        );
    }

    @AfterAll
    static void stopContext() {

        if (context != null) {
            context.close();
        }
    }

    @BeforeEach
    void setUp() {

        purchaseService =
                context.getBean(PurchaseService.class);

        receivingService =
                context.getBean(PurchaseReceivingService.class);

        queries =
                context.getBean(InventoryQueries.class);

        initializer =
                context.getBean(JpaStockInitializer.class);

        tx = new TransactionTemplate(
                context.getBean(JpaTransactionManager.class)
        );

        em = SharedEntityManagerCreator
                .createSharedEntityManager(
                        context.getBean(EntityManagerFactory.class)
                );

        assertTrue(
                AopUtils.isAopProxy(receivingService),
                "Receiving service must use Spring transactions"
        );

        String email =
                UUID.randomUUID() + "@example.test";

        actorId = tx.execute(status ->
                ((Number) em.createNativeQuery("""
                        INSERT INTO users
                        (full_name, email, password_hash, role_id)
                        VALUES (
                            'Purchase test actor',
                            :email,
                            'fixture',
                            (SELECT id FROM roles WHERE name = 'ADMIN')
                        )
                        RETURNING id
                        """)
                        .setParameter("email", email)
                        .getSingleResult()).longValue()
        );

        supplierId = tx.execute(status ->
                ((Number) em.createNativeQuery("""
                        INSERT INTO suppliers
                        (supplier_name, phone)
                        VALUES (:name, '0770000000')
                        RETURNING id
                        """)
                        .setParameter(
                                "name",
                                "Supplier-" + UUID.randomUUID()
                        )
                        .getSingleResult()).longValue()
        );

        principal =
                org.springframework.security.core.userdetails.User
                        .withUsername(email)
                        .password("fixture")
                        .roles("ADMIN")
                        .build();

        SecurityContextHolder.getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken
                                .authenticated(
                                        principal,
                                        null,
                                        principal.getAuthorities()
                                )
                );
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private long createProduct(boolean initializeStock) {

        long productId = tx.execute(status ->
                ((Number) em.createNativeQuery("""
                        INSERT INTO products
                        (
                            product_code,
                            product_name,
                            category_id,
                            cost_price,
                            selling_price
                        )
                        VALUES (
                            :code,
                            'Purchase fixture product',
                            (
                                SELECT id FROM categories
                                WHERE category_name = 'Inventory fixture'
                            ),
                            10.00,
                            20.00
                        )
                        RETURNING id
                        """)
                        .setParameter(
                                "code",
                                UUID.randomUUID().toString()
                        )
                        .getSingleResult()).longValue()
        );

        if (initializeStock) {
            initializer.initializeStock(productId);
        }

        return productId;
    }

    private PurchaseItemRequest item(
            long productId,
            int quantity
    ) {

        return new PurchaseItemRequest(
                productId,
                quantity,
                new BigDecimal("10.00")
        );
    }

    private long createDraft(
            PurchaseItemRequest... items
    ) {

        var request = new CreatePurchaseRequest(
                supplierId,
                "INV-" + UUID.randomUUID(),
                LocalDate.now(),
                BigDecimal.ZERO,
                List.of(items)
        );

        return purchaseService
                .createPurchase(request, actorId)
                .id();
    }

    private PurchaseStatus persistedStatus(long purchaseId) {

        return tx.execute(status ->
                em.find(Purchase.class, purchaseId)
                        .getStatus()
        );
    }

    private List<StockMovementView> movements(
            long productId,
            long purchaseId
    ) {

        return queries.searchStockMovements(
                new StockMovementQuery(
                        productId,
                        MovementKind.PURCHASE_RECEIVE,
                        "PURCHASE",
                        purchaseId,
                        null,
                        null,
                        null
                ),
                new PageQuery(0, 10)
        ).getContent();
    }

    @Test
    void shouldPersistReceivedPurchaseStatus() {

        long productId = createProduct(true);

        long purchaseId =
                createDraft(item(productId, 5));

        assertEquals(
                PurchaseStatus.DRAFT,
                persistedStatus(purchaseId)
        );

        receivingService.receivePurchase(purchaseId);

        assertEquals(
                PurchaseStatus.RECEIVED,
                persistedStatus(purchaseId)
        );
    }

    @Test
    void shouldIncreaseInventoryQuantity() {

        long productId = createProduct(true);

        long purchaseId =
                createDraft(item(productId, 10));

        assertEquals(
                0,
                queries.getCurrentStock(productId).quantity()
        );

        receivingService.receivePurchase(purchaseId);

        assertEquals(
                10,
                queries.getCurrentStock(productId).quantity()
        );
    }

    @Test
    void shouldPersistPurchaseStockMovement() {

        long productId = createProduct(true);

        long purchaseId =
                createDraft(item(productId, 7));

        receivingService.receivePurchase(purchaseId);

        var history = movements(productId, purchaseId);

        assertEquals(1, history.size());

        var movement = history.getFirst();

        assertEquals(
                MovementKind.PURCHASE_RECEIVE,
                movement.movementType()
        );

        assertEquals(7, movement.quantityChange());

        assertEquals(7, movement.quantityAfter());

        assertEquals(
                purchaseId,
                movement.context().referenceId()
        );

        assertEquals(
                "PURCHASE",
                movement.context().referenceType()
        );

        assertEquals(
                actorId,
                movement.context().actor().userId()
        );
    }

    @Test
    void shouldReceiveMultipleProducts() {

        long first = createProduct(true);

        long second = createProduct(true);

        long purchaseId = createDraft(
                item(second, 8),
                item(first, 5)
        );

        receivingService.receivePurchase(purchaseId);

        assertEquals(
                5,
                queries.getCurrentStock(first).quantity()
        );

        assertEquals(
                8,
                queries.getCurrentStock(second).quantity()
        );

        assertEquals(
                1,
                movements(first, purchaseId).size()
        );

        assertEquals(
                1,
                movements(second, purchaseId).size()
        );

        assertEquals(
                PurchaseStatus.RECEIVED,
                persistedStatus(purchaseId)
        );
    }

    @Test
    void shouldPreventDuplicateReceiving() {

        long productId = createProduct(true);

        long purchaseId =
                createDraft(item(productId, 6));

        receivingService.receivePurchase(purchaseId);

        var exception = assertThrows(
                ResponseStatusException.class,
                () -> receivingService.receivePurchase(purchaseId)
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        assertEquals(
                6,
                queries.getCurrentStock(productId).quantity()
        );

        assertEquals(
                1,
                movements(productId, purchaseId).size()
        );
    }

    @Test
    void shouldRollbackWhenLaterProductStockIsMissing() {

        long first = createProduct(true);

        long second = createProduct(false);

        assertTrue(first < second);

        long purchaseId = createDraft(
                item(second, 8),
                item(first, 5)
        );

        var exception = assertThrows(
                ResponseStatusException.class,
                () -> receivingService.receivePurchase(purchaseId)
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        assertEquals(
                PurchaseStatus.DRAFT,
                persistedStatus(purchaseId)
        );

        assertEquals(
                0,
                queries.getCurrentStock(first).quantity()
        );

        assertTrue(
                movements(first, purchaseId).isEmpty()
        );

        assertTrue(
                movements(second, purchaseId).isEmpty()
        );
    }
}