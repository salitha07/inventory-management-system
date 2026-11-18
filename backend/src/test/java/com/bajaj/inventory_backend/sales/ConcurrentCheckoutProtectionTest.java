package com.bajaj.inventory_backend.sales;

import com.bajaj.inventory_backend.common.exception.InsufficientStockException;
import com.bajaj.inventory_backend.entity.Role;
import com.bajaj.inventory_backend.entity.RoleName;
import com.bajaj.inventory_backend.entity.User;
import com.bajaj.inventory_backend.inventory.entity.StockMovementType;
import com.bajaj.inventory_backend.inventory.service.InventoryService;
import com.bajaj.inventory_backend.product.dto.ProductPriceDetails;
import com.bajaj.inventory_backend.product.service.ProductCatalogService;
import com.bajaj.inventory_backend.repository.UserRepository;
import com.bajaj.inventory_backend.sales.dto.SaleCreateRequest;
import com.bajaj.inventory_backend.sales.dto.SaleItemRequest;
import com.bajaj.inventory_backend.sales.dto.SaleResponse;
import com.bajaj.inventory_backend.sales.validation.CheckoutValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Concurrency and transaction protection test suite for POS checkout.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Overselling prevention during high-velocity simultaneous checkouts.</li>
 *   <li>Approved InventoryService locking strategy (pessimistic row serialization & deadlock prevention).</li>
 *   <li>Transaction atomicity: complete rollback when subsequent items fail stock deduction.</li>
 *   <li>Non-negative stock invariant (available stock never dips below zero).</li>
 *   <li>Proper handling of {@link InsufficientStockException} (HTTP 409).</li>
 *   <li>Zero direct updates to product stock from sales layer.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ConcurrentCheckoutProtectionTest {

    private SalesService salesService;
    private CheckoutValidator checkoutValidator;

    @Mock
    private SalesRepository salesRepository;

    @Mock
    private ProductCatalogService productCatalogService;

    @Mock
    private UserRepository userRepository;

    private User testCashier;

    @BeforeEach
    void setUp() {
        checkoutValidator = new CheckoutValidator();
        Role cashierRole = new Role();
        cashierRole.setName(RoleName.CASHIER);
        testCashier = new User();
        testCashier.setFullName("Sunil Sharma");
        testCashier.setEmail("sunil.sharma@bajajdealer.com");
        testCashier.setRole(cashierRole);

        // Default mock behaviors for sales repository
        lenient().when(salesRepository.save(any(Sale.class))).thenAnswer(invocation -> {
            Sale s = invocation.getArgument(0);
            if (s.getId() == null) {
                s.setId(ThreadLocalRandom.current().nextLong(1000, 999999));
            }
            return s;
        });
    }

    /**
     * Thread-safe mock implementation of {@link InventoryService} simulating Member 6's
     * approved row-level pessimistic locking strategy (`SELECT ... FOR UPDATE` / atomic conditional updates).
     */
    static class ConcurrentMockInventoryService implements InventoryService {

        private final ConcurrentMap<Long, Integer> stockMap = new ConcurrentHashMap<>();
        private final ConcurrentMap<Long, BigDecimal> costMap = new ConcurrentHashMap<>();
        private final ConcurrentMap<Long, ReentrantLock> rowLockMap = new ConcurrentHashMap<>();
        private final ConcurrentMap<Long, List<Long>> threadLockAcquisitionMap = new ConcurrentHashMap<>();

        public void setStock(Long productId, int quantity, BigDecimal unitCost) {
            stockMap.put(productId, quantity);
            costMap.put(productId, unitCost);
            rowLockMap.putIfAbsent(productId, new ReentrantLock());
        }

        private ReentrantLock getRowLock(Long productId) {
            return rowLockMap.computeIfAbsent(productId, id -> new ReentrantLock());
        }

        @Override
        public boolean isStockAvailable(Long productId, Integer requestedQuantity) {
            Integer available = stockMap.getOrDefault(productId, 0);
            return available >= requestedQuantity;
        }

        @Override
        public Integer getAvailableStock(Long productId) {
            return stockMap.getOrDefault(productId, 0);
        }

        @Override
        public void validateStockAvailability(Long productId, Integer requestedQuantity) {
            Integer available = stockMap.getOrDefault(productId, 0);
            if (available < requestedQuantity) {
                throw new InsufficientStockException(productId, "Part #" + productId, requestedQuantity, available);
            }
        }

        @Override
        public void deductStock(Long productId, Integer quantity, StockMovementType movementType, Long referenceId) {
            ReentrantLock rowLock = getRowLock(productId);
            // Simulate PostgreSQL row-level pessimistic write lock (SELECT ... FOR UPDATE)
            rowLock.lock();
            try {
                long threadId = Thread.currentThread().threadId();
                threadLockAcquisitionMap.computeIfAbsent(threadId, t -> new CopyOnWriteArrayList<>()).add(productId);

                Integer current = stockMap.getOrDefault(productId, 0);
                if (current < quantity) {
                    throw new InsufficientStockException(productId, "Part #" + productId, quantity, current);
                }
                stockMap.put(productId, current - quantity);
            } finally {
                rowLock.unlock();
            }
        }

        @Override
        public BigDecimal getCurrentUnitCost(Long productId) {
            return costMap.getOrDefault(productId, new BigDecimal("100.00"));
        }

        @Override
        public void restoreStock(Long productId, Integer quantity, StockMovementType movementType, Long referenceId) {
            ReentrantLock rowLock = getRowLock(productId);
            rowLock.lock();
            try {
                stockMap.compute(productId, (id, current) -> (current == null ? 0 : current) + quantity);
            } finally {
                rowLock.unlock();
            }
        }

        public ConcurrentMap<Long, List<Long>> getThreadLockAcquisitionMap() {
            return threadLockAcquisitionMap;
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Test 1: Simultaneous Checkouts for Limited Stock (Anti-Overselling)
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Prevents overselling when 20 simultaneous threads compete for 5 items in stock")
    void shouldPreventOverselling_whenMultipleThreadsCompeteForLimitedStock() throws Exception {
        // Arrange
        Long productId = 101L;
        int initialStock = 5;
        int totalCashiers = 20;

        ConcurrentMockInventoryService mockInventory = new ConcurrentMockInventoryService();
        mockInventory.setStock(productId, initialStock, new BigDecimal("160.00"));

        lenient().when(productCatalogService.getProductDetails(productId))
                .thenReturn(new ProductPriceDetails(productId, "DH101048", "Clutch Plate Assembly", new BigDecimal("220.00"), true));

        salesService = new SalesService(salesRepository, checkoutValidator, mockInventory, productCatalogService, userRepository);

        ExecutorService executor = Executors.newFixedThreadPool(totalCashiers);
        CyclicBarrier barrier = new CyclicBarrier(totalCashiers);

        AtomicInteger successfulCheckouts = new AtomicInteger(0);
        AtomicInteger rejectedCheckouts = new AtomicInteger(0);
        List<Throwable> unexpectedErrors = new CopyOnWriteArrayList<>();

        // Act: All 20 cashier threads attempt to sell 1 unit simultaneously
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < totalCashiers; i++) {
            futures.add(executor.submit(() -> {
                try {
                    barrier.await(); // Synchronize all threads to start at the exact same millisecond
                    SaleCreateRequest request = new SaleCreateRequest(
                            null, // Walk-in customer
                            PaymentMethod.CASH,
                            new BigDecimal("220.00"), // Exact cash
                            null,
                            BigDecimal.ZERO,
                            List.of(new SaleItemRequest(productId, 1))
                    );

                    SaleResponse response = salesService.processCheckout(request, testCashier);
                    if (response != null && response.id() != null) {
                        successfulCheckouts.incrementAndGet();
                    }
                } catch (InsufficientStockException ex) {
                    // Expected when stock runs out (HTTP 409)
                    rejectedCheckouts.incrementAndGet();
                } catch (Exception ex) {
                    unexpectedErrors.add(ex);
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // Assert: Invariants
        assertTrue(unexpectedErrors.isEmpty(), "Unexpected errors during concurrent checkouts: " + unexpectedErrors);
        assertEquals(5, successfulCheckouts.get(), "Exactly 5 checkouts must succeed because initial stock was 5");
        assertEquals(15, rejectedCheckouts.get(), "Remaining 15 checkouts must be rejected with InsufficientStockException");
        assertEquals(0, mockInventory.getAvailableStock(productId), "Remaining stock must be exactly 0, never negative");
    }

    // ─────────────────────────────────────────────────────────────
    //  Test 2: Multi-Item Deadlock Prevention with Opposing Item Order
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Prevents deadlocks when concurrent checkouts contain shared products in reverse order")
    void shouldPreventDeadlock_whenConcurrentCheckoutsContainItemsInOppositeOrder() throws Exception {
        // Arrange
        Long productA = 101L;
        Long productB = 205L;

        ConcurrentMockInventoryService mockInventory = new ConcurrentMockInventoryService();
        mockInventory.setStock(productA, 10, new BigDecimal("150.00"));
        mockInventory.setStock(productB, 10, new BigDecimal("350.00"));

        lenient().when(productCatalogService.getProductDetails(productA))
                .thenReturn(new ProductPriceDetails(productA, "DH101048", "Clutch Plate", new BigDecimal("220.00"), true));
        lenient().when(productCatalogService.getProductDetails(productB))
                .thenReturn(new ProductPriceDetails(productB, "36JZ0001", "Engine Oil", new BigDecimal("430.00"), true));

        salesService = new SalesService(salesRepository, checkoutValidator, mockInventory, productCatalogService, userRepository);

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        AtomicInteger completedCount = new AtomicInteger(0);
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        // Act: Half the threads submit (productA, productB), the other half submit (productB, productA)
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final boolean reverseOrder = (i % 2 == 1);
            futures.add(executor.submit(() -> {
                try {
                    barrier.await();

                    List<SaleItemRequest> items = reverseOrder
                            ? List.of(new SaleItemRequest(productB, 1), new SaleItemRequest(productA, 1))
                            : List.of(new SaleItemRequest(productA, 1), new SaleItemRequest(productB, 1));

                    SaleCreateRequest request = new SaleCreateRequest(
                            null,
                            PaymentMethod.CASH,
                            new BigDecimal("650.00"),
                            null,
                            BigDecimal.ZERO,
                            items
                    );

                    salesService.processCheckout(request, testCashier);
                    completedCount.incrementAndGet();
                } catch (Exception ex) {
                    errors.add(ex);
                }
            }));
        }

        // Must complete promptly without deadlocking
        for (Future<?> f : futures) {
            f.get(5, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // Assert
        assertTrue(errors.isEmpty(), "No thread should have deadlocked or failed: " + errors);
        assertEquals(threads, completedCount.get(), "All checkouts should complete smoothly");
        assertEquals(10 - threads, mockInventory.getAvailableStock(productA));
        assertEquals(10 - threads, mockInventory.getAvailableStock(productB));

        // Verify that every thread strictly acquired locks in ascending productId order: 101 then 205
        ConcurrentMap<Long, List<Long>> threadMap = mockInventory.getThreadLockAcquisitionMap();
        assertFalse(threadMap.isEmpty(), "Threads must have acquired locks");
        for (Map.Entry<Long, List<Long>> entry : threadMap.entrySet()) {
            List<Long> acquisitions = entry.getValue();
            assertEquals(2, acquisitions.size(), "Each checkout thread must acquire exactly 2 item locks");
            assertEquals(productA, acquisitions.get(0), "Thread must lock lower Product 101 first");
            assertEquals(productB, acquisitions.get(1), "Thread must lock higher Product 205 second");
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Test 3: Transaction Atomicity & Rollback Integrity
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Preserves atomicity and rolls back entire transaction if any line item fails deduction")
    void shouldRollbackEntireSale_whenSecondItemHasInsufficientStock() {
        // Arrange
        Long availableProduct = 101L;
        Long exhaustedProduct = 205L;

        ConcurrentMockInventoryService mockInventory = new ConcurrentMockInventoryService();
        mockInventory.setStock(availableProduct, 10, new BigDecimal("150.00"));
        mockInventory.setStock(exhaustedProduct, 0, new BigDecimal("350.00")); // 0 stock

        lenient().when(productCatalogService.getProductDetails(availableProduct))
                .thenReturn(new ProductPriceDetails(availableProduct, "DH101048", "Clutch Plate", new BigDecimal("220.00"), true));
        lenient().when(productCatalogService.getProductDetails(exhaustedProduct))
                .thenReturn(new ProductPriceDetails(exhaustedProduct, "36JZ0001", "Engine Oil", new BigDecimal("430.00"), true));

        salesService = new SalesService(salesRepository, checkoutValidator, mockInventory, productCatalogService, userRepository);

        SaleCreateRequest request = new SaleCreateRequest(
                null,
                PaymentMethod.CASH,
                new BigDecimal("870.00"), // 2 * 220 + 1 * 430 = 870
                null,
                BigDecimal.ZERO,
                List.of(
                        new SaleItemRequest(availableProduct, 2),
                        new SaleItemRequest(exhaustedProduct, 1)
                )
        );

        // Act & Assert
        InsufficientStockException ex = assertThrows(InsufficientStockException.class,
                () -> salesService.processCheckout(request, testCashier));

        assertEquals(exhaustedProduct, ex.getProductId());
        // Verify stock of available product remained completely intact (10)
        assertEquals(10, mockInventory.getAvailableStock(availableProduct),
                "Available product stock must not be deducted when transaction fails");
        assertEquals(0, mockInventory.getAvailableStock(exhaustedProduct));
    }

    // ─────────────────────────────────────────────────────────────
    //  Test 4: Non-Negative Stock Invariant with Variable Bulk Quantities
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Guarantees non-negative stock invariant with differing demand quantities")
    void shouldMaintainNonNegativeStock_withVaryingPurchaseQuantities() throws Exception {
        // Starting stock: 10 units
        Long productId = 312L;
        int initialStock = 10;

        ConcurrentMockInventoryService mockInventory = new ConcurrentMockInventoryService();
        mockInventory.setStock(productId, initialStock, new BigDecimal("180.00"));

        lenient().when(productCatalogService.getProductDetails(productId))
                .thenReturn(new ProductPriceDetails(productId, "DJ151034", "Brake Shoe Set", new BigDecimal("250.00"), true));

        salesService = new SalesService(salesRepository, checkoutValidator, mockInventory, productCatalogService, userRepository);

        // Demand quantities: 4 cashiers asking for 4, 4, 3, 2 units (total demand 13 units > 10 available)
        int[] demands = {4, 4, 3, 2};
        int numCashiers = demands.length;

        ExecutorService executor = Executors.newFixedThreadPool(numCashiers);
        CyclicBarrier barrier = new CyclicBarrier(numCashiers);
        AtomicInteger totalSold = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int demand : demands) {
            futures.add(executor.submit(() -> {
                try {
                    barrier.await();
                    BigDecimal total = new BigDecimal("250.00").multiply(BigDecimal.valueOf(demand));
                    SaleCreateRequest request = new SaleCreateRequest(
                            null,
                            PaymentMethod.CASH,
                            total,
                            null,
                            BigDecimal.ZERO,
                            List.of(new SaleItemRequest(productId, demand))
                    );

                    SaleResponse resp = salesService.processCheckout(request, testCashier);
                    if (resp != null) {
                        totalSold.addAndGet(demand);
                    }
                } catch (InsufficientStockException expected) {
                    // Rejected
                } catch (Exception ex) {
                    fail("Unexpected failure: " + ex.getMessage());
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get(5, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // Invariants:
        // totalSold <= initialStock (10)
        assertTrue(totalSold.get() <= initialStock, "Total sold units (" + totalSold.get() + ") cannot exceed initial stock (10)");
        // remaining stock == initialStock - totalSold
        int remainingStock = mockInventory.getAvailableStock(productId);
        assertEquals(initialStock - totalSold.get(), remainingStock);
        assertTrue(remainingStock >= 0, "Stock must never be negative, was: " + remainingStock);
    }

    // ─────────────────────────────────────────────────────────────
    //  Test 5: Unique Sale Number Generation Under High Concurrency
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Generates unique sale numbers without collisions under concurrent checkout requests")
    void shouldGenerateUniqueSaleNumbers_underConcurrentTraffic() throws Exception {
        int threads = 30;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);

        Set<String> generatedNumbers = ConcurrentHashMap.newKeySet();
        List<Future<?>> futures = new ArrayList<>();

        salesService = new SalesService(salesRepository, checkoutValidator);

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                try {
                    barrier.await();
                    String saleNumber = salesService.generateNextSaleNumber();
                    generatedNumbers.add(saleNumber);
                } catch (Exception ex) {
                    fail("Failed generating sale number: " + ex.getMessage());
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get(5, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertEquals(threads, generatedNumbers.size(), "Every thread must receive a unique sale number without collisions");
        for (String num : generatedNumbers) {
            assertTrue(num.startsWith("INV-"), "Sale number must start with INV-");
        }
    }
}
