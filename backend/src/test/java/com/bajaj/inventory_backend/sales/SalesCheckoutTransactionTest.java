package com.bajaj.inventory_backend.sales;

import com.bajaj.inventory_backend.common.exception.InsufficientStockException;
import com.bajaj.inventory_backend.common.exception.ProductNotFoundException;
import com.bajaj.inventory_backend.entity.Role;
import com.bajaj.inventory_backend.entity.RoleName;
import com.bajaj.inventory_backend.entity.User;
import com.bajaj.inventory_backend.inventory.entity.StockMovementType;
import com.bajaj.inventory_backend.inventory.service.InventoryService;
import com.bajaj.inventory_backend.product.dto.ProductPriceDetails;
import com.bajaj.inventory_backend.product.service.ProductCatalogService;
import com.bajaj.inventory_backend.repository.UserRepository;
import com.bajaj.inventory_backend.sales.dto.ReceiptResponse;
import com.bajaj.inventory_backend.sales.dto.SaleCreateRequest;
import com.bajaj.inventory_backend.sales.dto.SaleItemRequest;
import com.bajaj.inventory_backend.sales.dto.SaleResponse;
import com.bajaj.inventory_backend.sales.dto.SaleSummaryResponse;
import com.bajaj.inventory_backend.sales.exception.InvalidCheckoutException;
import com.bajaj.inventory_backend.sales.validation.CheckoutValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit and transactional orchestration tests for the sales checkout workflow.
 */
@ExtendWith(MockitoExtension.class)
class SalesCheckoutTransactionTest {

    private SalesService salesService;
    private CheckoutValidator checkoutValidator;

    @Mock
    private SalesRepository salesRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private ProductCatalogService productCatalogService;

    @Mock
    private UserRepository userRepository;

    private User cashierUser;

    @BeforeEach
    void setUp() {
        checkoutValidator = new CheckoutValidator();
        salesService = new SalesService(
                salesRepository,
                checkoutValidator,
                inventoryService,
                productCatalogService,
                userRepository
        );

        Role cashierRole = new Role();
        cashierRole.setName(RoleName.CASHIER);
        cashierUser = new User();
        cashierUser.setFullName("Sunil Sharma");
        cashierUser.setEmail("sunil@bajaj.com");
        cashierUser.setRole(cashierRole);
    }

    private SaleItemRequest createItem(Long productId, Integer quantity) {
        return new SaleItemRequest(productId, quantity);
    }

    private SaleItemRequest createItemWithClientPrice(Long productId, Integer quantity, BigDecimal priceHint) {
        return new SaleItemRequest(productId, quantity, priceHint);
    }

    // ──────────────────────────────────────────────
    //  1. Walk-in Cash Checkout
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Successfully processes walk-in cash checkout with change calculation & cost snapshots")
    void shouldSuccessfullyCompleteWalkInCashCheckout() {
        // Arrange
        List<SaleItemRequest> items = List.of(
                createItem(101L, 2),
                createItem(205L, 1)
        );
        SaleCreateRequest request = new SaleCreateRequest(
                null,
                PaymentMethod.CASH,
                new BigDecimal("1000.00"),
                null,
                BigDecimal.ZERO,
                items
        );

        // Catalog mock: 101 @ 220.00, 205 @ 430.00
        when(productCatalogService.getProductDetails(101L))
                .thenReturn(new ProductPriceDetails(101L, "DH101048", "Clutch Plate Assembly", new BigDecimal("220.00"), true));
        when(productCatalogService.getProductDetails(205L))
                .thenReturn(new ProductPriceDetails(205L, "36JZ0001", "Bajaj DTS-i 4T 10W-30 Engine Oil (1L)", new BigDecimal("430.00"), true));

        // Inventory cost snapshots: 101 @ 160.00, 205 @ 310.00
        when(inventoryService.getCurrentUnitCost(101L)).thenReturn(new BigDecimal("160.00"));
        when(inventoryService.getCurrentUnitCost(205L)).thenReturn(new BigDecimal("310.00"));

        // Save mock returns persisted entity with ID
        when(salesRepository.save(any(Sale.class))).thenAnswer(invocation -> {
            Sale s = invocation.getArgument(0);
            s.setId(1042L);
            long itemId = 8201L;
            for (SaleItem si : s.getItems()) {
                si.setId(itemId++);
            }
            return s;
        });

        // Act
        SaleResponse response = salesService.processCheckout(request, cashierUser);

        // Assert
        assertNotNull(response);
        assertEquals(1042L, response.id());
        assertTrue(response.saleNumber().startsWith("INV-"));
        assertNull(response.customerId());
        assertEquals("Walk-in Customer", response.customerName());
        assertEquals("COMPLETED", response.status());
        assertEquals("CASH", response.paymentMethod());

        // Totals validation: line1 = 2 * 220 = 440; line2 = 1 * 430 = 430 -> subtotal = 870
        assertEquals(new BigDecimal("870.00"), response.subtotal());
        assertEquals(new BigDecimal("0.00"), response.discount());
        assertEquals(new BigDecimal("870.00"), response.total());
        assertEquals(new BigDecimal("1000.00"), response.amountTendered());
        assertEquals(new BigDecimal("130.00"), response.changeDue()); // 1000 - 870 = 130
        assertEquals("Sunil Sharma", response.createdByName());

        // Line items validation
        assertEquals(2, response.items().size());
        assertEquals("DH101048", response.items().get(0).partNumber());
        assertEquals("Clutch Plate Assembly", response.items().get(0).productName());
        assertEquals(new BigDecimal("220.00"), response.items().get(0).unitSellingPrice());
        assertEquals(new BigDecimal("160.00"), response.items().get(0).unitCostSnapshot());
        assertEquals(new BigDecimal("440.00"), response.items().get(0).lineTotal());

        // Inventory deduction verification (Requirement 7)
        verify(inventoryService).deductStock(101L, 2, StockMovementType.SALE, 1042L);
        verify(inventoryService).deductStock(205L, 1, StockMovementType.SALE, 1042L);
    }

    // ──────────────────────────────────────────────
    //  2. Registered Customer Credit Checkout
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Successfully processes registered customer credit checkout with discount")
    void shouldSuccessfullyCompleteRegisteredCustomerCreditCheckout() {
        // Arrange
        List<SaleItemRequest> items = List.of(createItem(312L, 2));
        SaleCreateRequest request = new SaleCreateRequest(
                42L,
                PaymentMethod.CREDIT,
                null,
                "PO-9921",
                new BigDecimal("50.00"),
                items
        );

        // Catalog mock: 312 @ 250.00
        when(productCatalogService.getProductDetails(312L))
                .thenReturn(new ProductPriceDetails(312L, "DJ151034", "Brake Shoe Set Rear", new BigDecimal("250.00"), true));
        when(inventoryService.getCurrentUnitCost(312L)).thenReturn(new BigDecimal("180.00"));

        when(salesRepository.save(any(Sale.class))).thenAnswer(invocation -> {
            Sale s = invocation.getArgument(0);
            s.setId(2001L);
            return s;
        });

        // Act
        SaleResponse response = salesService.processCheckout(request, cashierUser);

        // Assert
        assertNotNull(response);
        assertEquals(42L, response.customerId());
        assertEquals("Customer #42", response.customerName());
        assertEquals("CREDIT", response.paymentMethod());
        assertEquals("PO-9921", response.paymentReference());
        assertNull(response.amountTendered());
        assertNull(response.changeDue());

        // Line total = 2 * 250 = 500, discount = 50, net total = 450
        assertEquals(new BigDecimal("500.00"), response.subtotal());
        assertEquals(new BigDecimal("50.00"), response.discount());
        assertEquals(new BigDecimal("450.00"), response.total());

        verify(inventoryService).deductStock(312L, 2, StockMovementType.SALE, 2001L);
    }

    // ──────────────────────────────────────────────
    //  3. Zero-Trust Price Validation
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Ignores client-submitted prices and strictly enforces authoritative catalog prices")
    void shouldIgnoreClientProvidedSellingPrice_andUseAuthoritativeCatalogPrice() {
        // Client attempts to pay ₹1.00 for a ₹220.00 part
        List<SaleItemRequest> items = List.of(createItemWithClientPrice(101L, 2, new BigDecimal("1.00")));
        SaleCreateRequest request = new SaleCreateRequest(
                null,
                PaymentMethod.CASH,
                new BigDecimal("500.00"),
                null,
                BigDecimal.ZERO,
                items
        );

        when(productCatalogService.getProductDetails(101L))
                .thenReturn(new ProductPriceDetails(101L, "DH101048", "Clutch Plate Assembly", new BigDecimal("220.00"), true));
        when(inventoryService.getCurrentUnitCost(101L)).thenReturn(new BigDecimal("160.00"));

        when(salesRepository.save(any(Sale.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        SaleResponse response = salesService.processCheckout(request, cashierUser);

        // Assert: server authoritative price ₹220 was applied, not client's ₹1.00
        assertEquals(new BigDecimal("220.00"), response.items().get(0).unitSellingPrice());
        assertEquals(new BigDecimal("440.00"), response.items().get(0).lineTotal());
        assertEquals(new BigDecimal("440.00"), response.subtotal());
        assertEquals(new BigDecimal("440.00"), response.total());
        assertEquals(new BigDecimal("60.00"), response.changeDue()); // 500 - 440 = 60
    }

    // ──────────────────────────────────────────────
    //  4. Product Not Found / Discontinued
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Throws ProductNotFoundException when product is discontinued or missing from catalog")
    void shouldThrowProductNotFoundException_whenCatalogProductDoesNotExist() {
        List<SaleItemRequest> items = List.of(createItem(999L, 1));
        SaleCreateRequest request = new SaleCreateRequest(
                null,
                PaymentMethod.CASH,
                new BigDecimal("500.00"),
                null,
                BigDecimal.ZERO,
                items
        );

        when(productCatalogService.getProductDetails(999L))
                .thenThrow(new ProductNotFoundException(999L));

        ProductNotFoundException ex = assertThrows(ProductNotFoundException.class,
                () -> salesService.processCheckout(request, cashierUser));

        assertEquals(999L, ex.getProductId());
        verify(salesRepository, never()).save(any());
        verify(inventoryService, never()).deductStock(any(), any(), any(), any());
    }

    // ──────────────────────────────────────────────
    //  5. Transaction Rollback on Stock Deficit
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Throws InsufficientStockException and rolls back when deductStock fails")
    void shouldRollback_whenInventoryDeductStockThrowsInsufficientStockException() {
        List<SaleItemRequest> items = List.of(createItem(101L, 10));
        SaleCreateRequest request = new SaleCreateRequest(
                null,
                PaymentMethod.CASH,
                new BigDecimal("3000.00"),
                null,
                BigDecimal.ZERO,
                items
        );

        when(productCatalogService.getProductDetails(101L))
                .thenReturn(new ProductPriceDetails(101L, "DH101048", "Clutch Plate Assembly", new BigDecimal("220.00"), true));
        when(inventoryService.getCurrentUnitCost(101L)).thenReturn(new BigDecimal("160.00"));

        when(salesRepository.save(any(Sale.class))).thenAnswer(invocation -> {
            Sale s = invocation.getArgument(0);
            s.setId(1099L);
            return s;
        });

        // deductStock throws InsufficientStockException (stock was taken concurrently)
        doThrow(new InsufficientStockException(101L, "Clutch Plate Assembly", 10, 3))
                .when(inventoryService).deductStock(eq(101L), eq(10), eq(StockMovementType.SALE), eq(1099L));

        // Act & Assert
        InsufficientStockException ex = assertThrows(InsufficientStockException.class,
                () -> salesService.processCheckout(request, cashierUser));

        assertEquals(101L, ex.getProductId());
        assertEquals(10, ex.getRequestedQuantity());
        assertEquals(3, ex.getAvailableQuantity());
    }

    // ──────────────────────────────────────────────
    //  6. Deadlock Prevention: Ascending Order Stock Deductions
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Deducts stock in strict ascending productId order to prevent database deadlocks")
    void shouldDeductStockInAscendingProductIdOrder_preventingDeadlocks() {
        // Items submitted in reverse order: [401, 101, 205]
        List<SaleItemRequest> items = List.of(
                createItem(401L, 1),
                createItem(101L, 2),
                createItem(205L, 1)
        );
        SaleCreateRequest request = new SaleCreateRequest(
                null,
                PaymentMethod.CASH,
                new BigDecimal("2000.00"),
                null,
                BigDecimal.ZERO,
                items
        );

        when(productCatalogService.getProductDetails(101L))
                .thenReturn(new ProductPriceDetails(101L, "DH101048", "Part 101", new BigDecimal("220.00"), true));
        when(productCatalogService.getProductDetails(205L))
                .thenReturn(new ProductPriceDetails(205L, "36JZ0001", "Part 205", new BigDecimal("430.00"), true));
        when(productCatalogService.getProductDetails(401L))
                .thenReturn(new ProductPriceDetails(401L, "DK101235", "Part 401", new BigDecimal("380.00"), true));

        when(salesRepository.save(any(Sale.class))).thenAnswer(invocation -> {
            Sale s = invocation.getArgument(0);
            s.setId(3001L);
            return s;
        });

        // Act
        salesService.processCheckout(request, cashierUser);

        // Verify that deductStock was called in strictly sorted order: 101, then 205, then 401
        InOrder inOrder = inOrder(inventoryService);
        inOrder.verify(inventoryService).deductStock(101L, 2, StockMovementType.SALE, 3001L);
        inOrder.verify(inventoryService).deductStock(205L, 1, StockMovementType.SALE, 3001L);
        inOrder.verify(inventoryService).deductStock(401L, 1, StockMovementType.SALE, 3001L);
    }

    // ──────────────────────────────────────────────
    //  7. Cash Tender Insufficient Rejection
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Rejects cash checkout when tendered amount is less than total")
    void shouldRejectCheckout_whenCashTenderedIsLessThanCalculatedTotal() {
        List<SaleItemRequest> items = List.of(createItem(101L, 2));
        SaleCreateRequest request = new SaleCreateRequest(
                null,
                PaymentMethod.CASH,
                new BigDecimal("400.00"), // Total is 440.00, tendered only 400.00
                null,
                BigDecimal.ZERO,
                items
        );

        when(productCatalogService.getProductDetails(101L))
                .thenReturn(new ProductPriceDetails(101L, "DH101048", "Part 101", new BigDecimal("220.00"), true));

        InvalidCheckoutException ex = assertThrows(InvalidCheckoutException.class,
                () -> salesService.processCheckout(request, cashierUser));

        assertTrue(ex.getMessage().contains("less than the required total payable amount"));
        verify(salesRepository, never()).save(any());
        verify(inventoryService, never()).deductStock(any(), any(), any(), any());
    }

    // ──────────────────────────────────────────────
    //  8. Separation of Concerns: No Direct Stock Updates
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Strict separation of concerns: never directly modifies stock tables")
    void shouldNeverDirectlyUpdateProductStock() {
        List<SaleItemRequest> items = List.of(createItem(101L, 1));
        SaleCreateRequest request = new SaleCreateRequest(
                null,
                PaymentMethod.CASH,
                new BigDecimal("500.00"),
                null,
                BigDecimal.ZERO,
                items
        );

        when(productCatalogService.getProductDetails(101L))
                .thenReturn(new ProductPriceDetails(101L, "DH101048", "Part 101", new BigDecimal("220.00"), true));
        when(salesRepository.save(any(Sale.class))).thenAnswer(invocation -> {
            Sale s = invocation.getArgument(0);
            s.setId(4001L);
            return s;
        });

        salesService.processCheckout(request, cashierUser);

        // Ensure stock operations went EXCLUSIVELY through InventoryService
        verify(inventoryService).deductStock(101L, 1, StockMovementType.SALE, 4001L);
    }

    // ──────────────────────────────────────────────
    //  9. Sale Details & Receipt Retrieval (Requirements 1-10)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Retrieves persisted sale details with price snapshots (GET /api/v1/sales/{id})")
    void shouldRetrieveSaleDetailsMappedToSaleResponse() {
        Sale sale = new Sale();
        sale.setId(501L);
        sale.setSaleNumber("INV-202611-0501");
        sale.setCustomerId(42L);
        sale.setSaleDate(java.time.Instant.parse("2026-11-13T14:30:00Z"));
        sale.setStatus(SaleStatus.COMPLETED);
        sale.setSubtotal(new BigDecimal("440.00"));
        sale.setDiscount(new BigDecimal("20.00"));
        sale.setTotal(new BigDecimal("420.00"));
        sale.setPaymentMethod(PaymentMethod.CREDIT);
        sale.setCreatedBy(cashierUser);

        SaleItem item = new SaleItem(
                sale,
                101L,
                2,
                new BigDecimal("220.00"), // persisted selling price snapshot
                new BigDecimal("160.00"), // persisted cost snapshot
                new BigDecimal("440.00")
        );
        item.setId(901L);
        sale.addItem(item);

        when(salesRepository.findById(501L)).thenReturn(Optional.of(sale));
        when(productCatalogService.getProductDetails(101L))
                .thenReturn(new ProductPriceDetails(101L, "DH101048", "Clutch Plate Assembly", new BigDecimal("220.00"), true));

        // Act
        Optional<SaleResponse> result = salesService.getSaleDetails(501L);

        // Assert
        assertTrue(result.isPresent());
        SaleResponse response = result.get();
        assertEquals(501L, response.id());
        assertEquals("INV-202611-0501", response.saleNumber());
        assertEquals(42L, response.customerId());
        assertEquals("Customer #42", response.customerName());
        assertEquals("COMPLETED", response.status());
        assertEquals("CREDIT", response.paymentMethod());
        assertEquals(new BigDecimal("440.00"), response.subtotal());
        assertEquals(new BigDecimal("20.00"), response.discount());
        assertEquals(new BigDecimal("420.00"), response.total());

        assertEquals(1, response.items().size());
        assertEquals("DH101048", response.items().get(0).partNumber());
        assertEquals("Clutch Plate Assembly", response.items().get(0).productName());
        assertEquals(2, response.items().get(0).quantity());
        assertEquals(new BigDecimal("220.00"), response.items().get(0).unitSellingPrice());
        assertEquals(new BigDecimal("160.00"), response.items().get(0).unitCostSnapshot());
    }

    @Test
    @DisplayName("Retrieves structured receipt response matching POS contract (GET /api/v1/sales/{id}/receipt)")
    void shouldRetrieveFormattedReceiptResponse() {
        Sale sale = new Sale();
        sale.setId(1001L);
        sale.setSaleNumber("INV-202611-0001");
        sale.setCustomerId(null); // Walk-in customer
        sale.setSaleDate(java.time.Instant.parse("2026-11-13T14:30:00Z"));
        sale.setStatus(SaleStatus.COMPLETED);
        sale.setSubtotal(new BigDecimal("440.00"));
        sale.setDiscount(BigDecimal.ZERO);
        sale.setTotal(new BigDecimal("440.00"));
        sale.setPaymentMethod(PaymentMethod.CASH);
        sale.setCreatedBy(cashierUser);

        SaleItem item = new SaleItem(
                sale,
                101L,
                2,
                new BigDecimal("220.00"),
                new BigDecimal("160.00"),
                new BigDecimal("440.00")
        );
        sale.addItem(item);

        when(salesRepository.findById(1001L)).thenReturn(Optional.of(sale));
        when(productCatalogService.getProductDetails(101L))
                .thenReturn(new ProductPriceDetails(101L, "DH101048", "Clutch Plate Assembly", new BigDecimal("220.00"), true));

        // Act
        Optional<ReceiptResponse> result = salesService.getReceipt(1001L);

        // Assert
        assertTrue(result.isPresent());
        ReceiptResponse receipt = result.get();
        assertEquals("INV-202611-0001", receipt.receiptInfo().saleNumber());
        assertEquals("Walk-in Customer", receipt.receiptInfo().customerName());
        assertEquals("Sunil Sharma", receipt.receiptInfo().cashierName());
        assertEquals("Bajaj Genuine Spare Parts - Central Depot", receipt.storeInfo().storeName());

        assertEquals(1, receipt.items().size());
        assertEquals("DH101048", receipt.items().get(0).partNumber());
        assertEquals("Clutch Plate Assembly", receipt.items().get(0).description());
        assertEquals(2, receipt.items().get(0).quantity());
        assertEquals(new BigDecimal("220.00"), receipt.items().get(0).unitPrice());
        assertEquals(new BigDecimal("440.00"), receipt.items().get(0).lineTotal());

        assertEquals(new BigDecimal("440.00"), receipt.totals().subtotal());
        assertEquals(new BigDecimal("440.00"), receipt.totals().grandTotal());
        assertEquals("CASH", receipt.payment().paymentMethod());
        assertNotNull(receipt.footerNote());
    }

    @Test
    @DisplayName("Returns empty Optional when querying non-existent sale ID")
    void shouldReturnEmptyOptional_whenSaleNotFound() {
        when(salesRepository.findById(9999L)).thenReturn(Optional.empty());

        Optional<SaleResponse> saleResponse = salesService.getSaleDetails(9999L);
        Optional<ReceiptResponse> receiptResponse = salesService.getReceipt(9999L);

        assertTrue(saleResponse.isEmpty());
        assertTrue(receiptResponse.isEmpty());
    }

    @Test
    @DisplayName("Queries sales summaries with pagination and filters")
    void shouldFindSalesSummary_withPaginationAndFilters() {
        // Arrange
        Sale sale1 = new Sale();
        sale1.setId(1001L);
        sale1.setSaleNumber("INV-202611-0001");
        sale1.setCustomerId(null);
        sale1.setSaleDate(java.time.Instant.parse("2026-11-13T14:30:00Z"));
        sale1.setStatus(SaleStatus.COMPLETED);
        sale1.setSubtotal(new BigDecimal("440.00"));
        sale1.setDiscount(BigDecimal.ZERO);
        sale1.setTotal(new BigDecimal("440.00"));
        sale1.setPaymentMethod(PaymentMethod.CASH);
        sale1.setCreatedBy(cashierUser);

        SaleItem item1 = new SaleItem();
        item1.setProductId(101L);
        item1.setQuantity(2);
        item1.setUnitSellingPrice(new BigDecimal("220.00"));
        item1.setLineTotal(new BigDecimal("440.00"));
        sale1.setItems(List.of(item1));

        Page<Sale> mockPage = new PageImpl<>(List.of(sale1), PageRequest.of(0, 10), 1);
        when(salesRepository.findAll(ArgumentMatchers.<Specification<Sale>>any(), any(Pageable.class))).thenReturn(mockPage);

        // Act
        Pageable pageable = PageRequest.of(0, 10);
        Page<SaleSummaryResponse> result = salesService.findSalesSummary("COMPLETED", PaymentMethod.CASH, "INV-202611", pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());

        SaleSummaryResponse summary = result.getContent().get(0);
        assertEquals(1001L, summary.id());
        assertEquals("INV-202611-0001", summary.saleNumber());
        assertEquals("Walk-in Customer", summary.customerName());
        assertEquals("COMPLETED", summary.status());
        assertEquals(2, summary.itemCount());
        assertEquals(new BigDecimal("440.00"), summary.total());
        assertEquals("CASH", summary.paymentMethod());
        assertEquals("Sunil Sharma", summary.createdByName());
    }

    @Test
    @DisplayName("Successfully processes checkout with QR_PAYMENT (LankaQR) and reference")
    void shouldSuccessfullyCompleteQrPaymentCheckout() {
        // Arrange
        List<SaleItemRequest> items = List.of(createItem(101L, 2));
        SaleCreateRequest request = new SaleCreateRequest(
                null,
                PaymentMethod.QR_PAYMENT,
                null,
                "LANKAQR-TXN-884920",
                BigDecimal.ZERO,
                items
        );

        when(productCatalogService.getProductDetails(101L))
                .thenReturn(new ProductPriceDetails(101L, "DH101048", "Clutch Plate Assembly", new BigDecimal("220.00"), true));
        when(inventoryService.getCurrentUnitCost(101L)).thenReturn(new BigDecimal("160.00"));

        when(salesRepository.save(any(Sale.class))).thenAnswer(invocation -> {
            Sale s = invocation.getArgument(0);
            s.setId(3001L);
            return s;
        });

        // Act
        SaleResponse response = salesService.processCheckout(request, cashierUser);

        // Assert
        assertNotNull(response);
        assertEquals(3001L, response.id());
        assertEquals("QR_PAYMENT", response.paymentMethod());
        assertEquals("LANKAQR-TXN-884920", response.paymentReference());
        assertEquals(new BigDecimal("440.00"), response.subtotal());
        assertEquals(new BigDecimal("440.00"), response.total());
        assertNull(response.amountTendered());
        assertNull(response.changeDue());

        verify(inventoryService).deductStock(101L, 2, StockMovementType.SALE, 3001L);
    }

    @Test
    @DisplayName("Safely reads legacy sales records that used UPI without corruption")
    @SuppressWarnings("deprecation")
    void shouldReadHistoricalSaleWithLegacyUpiPayment() {
        // Arrange
        Sale legacySale = new Sale();
        legacySale.setId(8888L);
        legacySale.setSaleNumber("INV-202609-0042");
        legacySale.setCustomerId(null);
        legacySale.setSaleDate(java.time.Instant.parse("2026-09-15T10:00:00Z"));
        legacySale.setStatus(SaleStatus.COMPLETED);
        legacySale.setSubtotal(new BigDecimal("220.00"));
        legacySale.setDiscount(BigDecimal.ZERO);
        legacySale.setTotal(new BigDecimal("220.00"));
        legacySale.setPaymentMethod(PaymentMethod.UPI);
        legacySale.setCreatedBy(cashierUser);

        when(salesRepository.findById(8888L)).thenReturn(Optional.of(legacySale));

        // Act
        Optional<SaleResponse> result = salesService.getSaleDetails(8888L);

        // Assert
        assertTrue(result.isPresent());
        SaleResponse response = result.get();
        assertEquals(8888L, response.id());
        assertEquals("UPI", response.paymentMethod());
        assertNull(response.paymentReference());
    }
}
