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
import com.bajaj.inventory_backend.sales.dto.SaleCreateRequest;
import com.bajaj.inventory_backend.sales.dto.SaleItemRequest;
import com.bajaj.inventory_backend.sales.exception.SalesExceptionHandler;
import com.bajaj.inventory_backend.sales.validation.CheckoutValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-End REST Controller & Service Integration Test Suite for the Sales module.
 *
 * <p>Exercises all key requirements:
 * <ul>
 *   <li>1. Product search & catalog lookup</li>
 *   <li>2. Add / remove / line item mapping</li>
 *   <li>3. Quantity updates & validation</li>
 *   <li>4. Subtotal calculation (zero-trust authoritative catalog pricing)</li>
 *   <li>5. Discount & net total calculation</li>
 *   <li>6. Checkout form validation (HTTP 400 Bad Request)</li>
 *   <li>7. Successful transactional checkout (HTTP 201 Created)</li>
 *   <li>8. Insufficient stock handling (HTTP 409 Conflict)</li>
 *   <li>9. Product not found / discontinued handling (HTTP 404 Not Found)</li>
 *   <li>10. Row locking & stock decrement delegation</li>
 *   <li>11. Invoice & thermal receipt views (GET /api/v1/sales/{id} and /receipt)</li>
 *   <li>12. Paginated sales history & filters (GET /api/v1/sales)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SalesManagementIntegrationTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

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
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        checkoutValidator = new CheckoutValidator();
        salesService = new SalesService(
                salesRepository,
                checkoutValidator,
                inventoryService,
                productCatalogService,
                userRepository
        );

        SalesController salesController = new SalesController(salesService);

        mockMvc = MockMvcBuilders.standaloneSetup(salesController)
                .setControllerAdvice(new SalesExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        Role cashierRole = new Role();
        cashierRole.setName(RoleName.CASHIER);
        cashierUser = new User();
        cashierUser.setFullName("Sunil Sharma");
        cashierUser.setEmail("sunil.sharma@bajajdealer.com");
        cashierUser.setRole(cashierRole);
    }

    // ─────────────────────────────────────────────────────────────
    //  7. Successful Checkout REST Integration (POST /api/v1/sales)
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/sales - Successfully executes walk-in cash checkout returning 201 Created")
    void shouldExecuteSuccessfulWalkInCashCheckout() throws Exception {
        // Arrange
        Long productId = 101L;
        when(productCatalogService.getProductDetails(productId))
                .thenReturn(new ProductPriceDetails(productId, "DH101048", "Clutch Plate Assembly", new BigDecimal("220.00"), true));
        when(inventoryService.getCurrentUnitCost(productId))
                .thenReturn(new BigDecimal("160.00"));

        when(salesRepository.save(any(Sale.class))).thenAnswer(inv -> {
            Sale s = inv.getArgument(0);
            s.setId(1001L);
            return s;
        });

        SaleCreateRequest request = new SaleCreateRequest(
                null, // Walk-in
                PaymentMethod.CASH,
                new BigDecimal("500.00"), // Tendered
                null,
                BigDecimal.ZERO, // No discount
                List.of(new SaleItemRequest(productId, 2)) // 2 * 220 = 440
        );

        // Act & Assert
        mockMvc.perform(post("/api/v1/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1001)))
                .andExpect(jsonPath("$.saleNumber", Matchers.startsWith("INV-")))
                .andExpect(jsonPath("$.customerName", is("Walk-in Customer")))
                .andExpect(jsonPath("$.subtotal", is(440.0)))
                .andExpect(jsonPath("$.discount", is(0.0)))
                .andExpect(jsonPath("$.total", is(440.0)))
                .andExpect(jsonPath("$.paymentMethod", is("CASH")))
                .andExpect(jsonPath("$.amountTendered", is(500.0)))
                .andExpect(jsonPath("$.changeDue", is(60.0)))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].productId", is(101)))
                .andExpect(jsonPath("$.items[0].partNumber", is("DH101048")))
                .andExpect(jsonPath("$.items[0].quantity", is(2)))
                .andExpect(jsonPath("$.items[0].unitSellingPrice", is(220.0)))
                .andExpect(jsonPath("$.items[0].unitCostSnapshot", is(160.0)))
                .andExpect(jsonPath("$.items[0].lineTotal", is(440.0)));

        // Verify inventory deduction delegation
        verify(inventoryService).deductStock(eq(101L), eq(2), eq(StockMovementType.SALE), eq(1001L));
    }

    @Test
    @DisplayName("POST /api/v1/sales - Successfully executes registered customer credit checkout")
    void shouldExecuteSuccessfulCustomerCreditCheckout() throws Exception {
        // Arrange
        Long productId = 205L;
        when(productCatalogService.getProductDetails(productId))
                .thenReturn(new ProductPriceDetails(productId, "36JZ0001", "Bajaj Engine Oil (1L)", new BigDecimal("430.00"), true));
        when(inventoryService.getCurrentUnitCost(productId))
                .thenReturn(new BigDecimal("320.00"));

        when(salesRepository.save(any(Sale.class))).thenAnswer(inv -> {
            Sale s = inv.getArgument(0);
            s.setId(1002L);
            return s;
        });

        SaleCreateRequest request = new SaleCreateRequest(
                42L, // Registered customer
                PaymentMethod.CREDIT,
                null,
                "PO-2026-NOV-42",
                new BigDecimal("30.00"), // 30 discount -> total 400
                List.of(new SaleItemRequest(productId, 1))
        );

        // Act & Assert
        mockMvc.perform(post("/api/v1/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1002)))
                .andExpect(jsonPath("$.customerId", is(42)))
                .andExpect(jsonPath("$.customerName", is("Customer #42")))
                .andExpect(jsonPath("$.subtotal", is(430.0)))
                .andExpect(jsonPath("$.discount", is(30.0)))
                .andExpect(jsonPath("$.total", is(400.0)))
                .andExpect(jsonPath("$.paymentMethod", is("CREDIT")))
                .andExpect(jsonPath("$.paymentReference", is("PO-2026-NOV-42")));

        verify(inventoryService).deductStock(eq(205L), eq(1), eq(StockMovementType.SALE), eq(1002L));
    }

    // ─────────────────────────────────────────────────────────────
    //  6. Checkout Validation REST Errors (HTTP 400 Bad Request)
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/sales - Rejects checkout when cash tendered is less than total (400 Bad Request)")
    void shouldReturn400_whenCashTenderedIsLessThanTotal() throws Exception {
        Long productId = 101L;
        when(productCatalogService.getProductDetails(productId))
                .thenReturn(new ProductPriceDetails(productId, "DH101048", "Clutch Plate", new BigDecimal("220.00"), true));

        SaleCreateRequest request = new SaleCreateRequest(
                null,
                PaymentMethod.CASH,
                new BigDecimal("100.00"), // Tendered 100 < Total 220
                null,
                BigDecimal.ZERO,
                List.of(new SaleItemRequest(productId, 1))
        );

        mockMvc.perform(post("/api/v1/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("is less than the required total payable amount")));
    }

    @Test
    @DisplayName("POST /api/v1/sales - Rejects credit terms for walk-in customer (400 Bad Request)")
    void shouldReturn400_whenCreditTenderUsedWithoutCustomer() throws Exception {
        Long productId = 101L;
        when(productCatalogService.getProductDetails(productId))
                .thenReturn(new ProductPriceDetails(productId, "DH101048", "Clutch Plate", new BigDecimal("220.00"), true));

        SaleCreateRequest request = new SaleCreateRequest(
                null, // Walk-in
                PaymentMethod.CREDIT,
                null,
                null,
                BigDecimal.ZERO,
                List.of(new SaleItemRequest(productId, 1))
        );

        mockMvc.perform(post("/api/v1/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("requires a registered customer account")));
    }

    @Test
    @DisplayName("POST /api/v1/sales - Rejects discount exceeding subtotal (400 Bad Request)")
    void shouldReturn400_whenDiscountExceedsSubtotal() throws Exception {
        Long productId = 101L;
        when(productCatalogService.getProductDetails(productId))
                .thenReturn(new ProductPriceDetails(productId, "DH101048", "Clutch Plate", new BigDecimal("220.00"), true));

        SaleCreateRequest request = new SaleCreateRequest(
                null,
                PaymentMethod.CASH,
                new BigDecimal("500.00"),
                null,
                new BigDecimal("300.00"), // Discount 300 > Subtotal 220
                List.of(new SaleItemRequest(productId, 1))
        );

        mockMvc.perform(post("/api/v1/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("cannot exceed authoritative subtotal")));
    }

    // ─────────────────────────────────────────────────────────────
    //  8. Insufficient Stock Handling (HTTP 409 Conflict)
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/sales - Returns 409 Conflict when requested quantity exceeds available stock")
    void shouldReturn409_whenInsufficientStockOnDeduction() throws Exception {
        Long productId = 101L;
        when(productCatalogService.getProductDetails(productId))
                .thenReturn(new ProductPriceDetails(productId, "DH101048", "Clutch Plate", new BigDecimal("220.00"), true));

        when(salesRepository.save(any(Sale.class))).thenAnswer(inv -> {
            Sale s = inv.getArgument(0);
            s.setId(1003L);
            return s;
        });

        // Inventory deduction fails due to stock deficit
        doThrow(new InsufficientStockException(productId, "Clutch Plate Assembly", 10, 3))
                .when(inventoryService).deductStock(eq(productId), eq(10), eq(StockMovementType.SALE), eq(1003L));

        SaleCreateRequest request = new SaleCreateRequest(
                null,
                PaymentMethod.CASH,
                new BigDecimal("2200.00"),
                null,
                BigDecimal.ZERO,
                List.of(new SaleItemRequest(productId, 10))
        );

        mockMvc.perform(post("/api/v1/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("Conflict")))
                .andExpect(jsonPath("$.message", containsString("Insufficient stock for product ID 101")))
                .andExpect(jsonPath("$.message", containsString("Requested: 10, Available: 3")));
    }

    // ─────────────────────────────────────────────────────────────
    //  9. Discontinued / Product Not Found (HTTP 404 Not Found)
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/sales - Returns 404 Not Found when product does not exist in catalog")
    void shouldReturn404_whenProductDoesNotExistInCatalog() throws Exception {
        Long missingProductId = 9999L;
        when(productCatalogService.getProductDetails(missingProductId))
                .thenThrow(new ProductNotFoundException(missingProductId));

        SaleCreateRequest request = new SaleCreateRequest(
                null,
                PaymentMethod.CASH,
                new BigDecimal("500.00"),
                null,
                BigDecimal.ZERO,
                List.of(new SaleItemRequest(missingProductId, 1))
        );

        mockMvc.perform(post("/api/v1/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", containsString("Product with ID 9999 was not found")));
    }

    // ─────────────────────────────────────────────────────────────
    //  11. Invoice & Thermal Receipt Views (GET /api/v1/sales/{id})
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/sales/{id} - Retrieves complete sale details for invoice rendering")
    void shouldRetrieveSaleDetailsById() throws Exception {
        Sale sale = new Sale();
        sale.setId(501L);
        sale.setSaleNumber("INV-202611-0501");
        sale.setCustomerId(null);
        sale.setSaleDate(Instant.parse("2026-11-15T10:00:00Z"));
        sale.setStatus(SaleStatus.COMPLETED);
        sale.setSubtotal(new BigDecimal("440.00"));
        sale.setDiscount(BigDecimal.ZERO);
        sale.setTotal(new BigDecimal("440.00"));
        sale.setPaymentMethod(PaymentMethod.CASH);
        sale.setCreatedBy(cashierUser);

        when(salesRepository.findById(501L)).thenReturn(Optional.of(sale));

        mockMvc.perform(get("/api/v1/sales/501"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(501)))
                .andExpect(jsonPath("$.saleNumber", is("INV-202611-0501")))
                .andExpect(jsonPath("$.customerName", is("Walk-in Customer")))
                .andExpect(jsonPath("$.total", is(440.0)))
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.createdByName", is("Sunil Sharma")));
    }

    @Test
    @DisplayName("GET /api/v1/sales/{id}/receipt - Retrieves formatted receipt optimized for 80mm thermal printing")
    void shouldRetrieveReceiptForThermalPrinting() throws Exception {
        Sale sale = new Sale();
        sale.setId(501L);
        sale.setSaleNumber("INV-202611-0501");
        sale.setCustomerId(null);
        sale.setSaleDate(Instant.parse("2026-11-15T10:00:00Z"));
        sale.setStatus(SaleStatus.COMPLETED);
        sale.setSubtotal(new BigDecimal("440.00"));
        sale.setDiscount(BigDecimal.ZERO);
        sale.setTotal(new BigDecimal("440.00"));
        sale.setPaymentMethod(PaymentMethod.CASH);
        sale.setCreatedBy(cashierUser);

        SaleItem item = new SaleItem(sale, 101L, 2, new BigDecimal("220.00"), new BigDecimal("160.00"), new BigDecimal("440.00"));
        sale.addItem(item);

        when(salesRepository.findById(501L)).thenReturn(Optional.of(sale));
        when(productCatalogService.getProductDetails(101L))
                .thenReturn(new ProductPriceDetails(101L, "DH101048", "Clutch Plate Assembly", new BigDecimal("220.00"), true));

        mockMvc.perform(get("/api/v1/sales/501/receipt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storeInfo.storeName", is("Bajaj Genuine Spare Parts - Central Depot")))
                .andExpect(jsonPath("$.receiptInfo.saleNumber", is("INV-202611-0501")))
                .andExpect(jsonPath("$.receiptInfo.cashierName", is("Sunil Sharma")))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].partNumber", is("DH101048")))
                .andExpect(jsonPath("$.totals.grandTotal", is(440.0)))
                .andExpect(jsonPath("$.totals.taxIncluded", is(67.12))) // 18% GST calculation
                .andExpect(jsonPath("$.payment.paymentMethod", is("CASH")));
    }

    // ─────────────────────────────────────────────────────────────
    //  12. Sales History REST Integration (GET /api/v1/sales)
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/sales - Retrieves paginated sales history summaries with filters")
    void shouldRetrievePaginatedSalesHistoryWithFilters() throws Exception {
        Sale sale1 = new Sale();
        sale1.setId(1L);
        sale1.setSaleNumber("INV-202611-0001");
        sale1.setCustomerId(null);
        sale1.setSaleDate(Instant.now());
        sale1.setStatus(SaleStatus.COMPLETED);
        sale1.setSubtotal(new BigDecimal("440.00"));
        sale1.setDiscount(BigDecimal.ZERO);
        sale1.setTotal(new BigDecimal("440.00"));
        sale1.setPaymentMethod(PaymentMethod.CASH);
        sale1.setCreatedBy(cashierUser);

        Page<Sale> mockPage = new PageImpl<>(List.of(sale1), PageRequest.of(0, 20), 1);
        when(salesRepository.findAll(ArgumentMatchers.<Specification<Sale>>any(), any(Pageable.class))).thenReturn(mockPage);

        mockMvc.perform(get("/api/v1/sales")
                        .param("page", "0")
                        .param("size", "20")
                        .param("status", "COMPLETED")
                        .param("paymentMethod", "CASH")
                        .param("search", "INV-202611"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].saleNumber", is("INV-202611-0001")))
                .andExpect(jsonPath("$.content[0].customerName", is("Walk-in Customer")))
                .andExpect(jsonPath("$.content[0].status", is("COMPLETED")))
                .andExpect(jsonPath("$.content[0].paymentMethod", is("CASH")))
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.totalPages", is(1)));
    }
}
