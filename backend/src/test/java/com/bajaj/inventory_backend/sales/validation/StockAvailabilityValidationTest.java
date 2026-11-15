package com.bajaj.inventory_backend.sales.validation;

import com.bajaj.inventory_backend.common.exception.InsufficientStockException;
import com.bajaj.inventory_backend.common.exception.ProductNotFoundException;
import com.bajaj.inventory_backend.inventory.service.InventoryService;
import com.bajaj.inventory_backend.sales.PaymentMethod;
import com.bajaj.inventory_backend.sales.SalesRepository;
import com.bajaj.inventory_backend.sales.SalesService;
import com.bajaj.inventory_backend.sales.dto.ErrorResponse;
import com.bajaj.inventory_backend.sales.dto.SaleCreateRequest;
import com.bajaj.inventory_backend.sales.dto.SaleItemRequest;
import com.bajaj.inventory_backend.sales.exception.InvalidCheckoutException;
import com.bajaj.inventory_backend.sales.exception.SalesExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for stock availability validation during sales checkout.
 *
 * <p>Validates adherence to:
 * <ul>
 *   <li>Centralized inventory service contract (Member 6).</li>
 *   <li>Rejection of insufficient stock with {@link InsufficientStockException} (HTTP 409 Conflict).</li>
 *   <li>Rejection of unavailable products with {@link ProductNotFoundException} (HTTP 404 Not Found).</li>
 *   <li>Zero direct updates to product stock from sales layer.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class StockAvailabilityValidationTest {

    private CheckoutValidator checkoutValidator;
    private SalesService salesService;
    private SalesExceptionHandler exceptionHandler;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private SalesRepository salesRepository;

    @Mock
    private HttpServletRequest httpServletRequest;

    @BeforeEach
    void setUp() {
        checkoutValidator = new CheckoutValidator();
        salesService = new SalesService(salesRepository, checkoutValidator, inventoryService);
        exceptionHandler = new SalesExceptionHandler();
    }

    private SaleItemRequest createItem(Long productId, Integer quantity) {
        return new SaleItemRequest(productId, quantity);
    }

    private SaleCreateRequest createRequest(Long customerId, PaymentMethod method,
                                            BigDecimal tendered, BigDecimal discount,
                                            List<SaleItemRequest> items) {
        return new SaleCreateRequest(customerId, method, tendered, null, discount, items);
    }

    // ──────────────────────────────────────────────
    //  Successful Stock Validation
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Stock check succeeds when all items have sufficient available stock")
    void shouldPass_whenAllItemsHaveSufficientStock() {
        List<SaleItemRequest> items = List.of(
                createItem(101L, 2),
                createItem(102L, 5)
        );

        doNothing().when(inventoryService).validateStockAvailability(101L, 2);
        doNothing().when(inventoryService).validateStockAvailability(102L, 5);

        assertDoesNotThrow(() -> checkoutValidator.validateStockAvailability(items, inventoryService));

        verify(inventoryService).validateStockAvailability(101L, 2);
        verify(inventoryService).validateStockAvailability(102L, 5);
        verifyNoMoreInteractions(inventoryService);
    }

    // ──────────────────────────────────────────────
    //  Insufficient Stock Scenario (HTTP 409)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Rejects checkout when requested quantity exceeds available stock")
    void shouldThrowInsufficientStockException_whenRequestedQuantityExceedsStock() {
        List<SaleItemRequest> items = List.of(createItem(101L, 5));

        doThrow(new InsufficientStockException(101L, "Clutch Plate Assembly", 5, 2))
                .when(inventoryService).validateStockAvailability(101L, 5);

        InsufficientStockException ex = assertThrows(InsufficientStockException.class,
                () -> checkoutValidator.validateStockAvailability(items, inventoryService));

        assertEquals(101L, ex.getProductId());
        assertEquals("Clutch Plate Assembly", ex.getProductName());
        assertEquals(5, ex.getRequestedQuantity());
        assertEquals(2, ex.getAvailableQuantity());
        assertTrue(ex.getMessage().contains("Insufficient stock for product ID 101"));
        assertTrue(ex.getMessage().contains("Requested: 5, Available: 2"));
    }

    @Test
    @DisplayName("In a multi-item cart, fails immediately when a subsequent item has insufficient stock")
    void shouldFailOnSecondItem_whenFirstItemAvailableButSecondItemInsufficient() {
        List<SaleItemRequest> items = List.of(
                createItem(101L, 1),
                createItem(102L, 10)
        );

        doNothing().when(inventoryService).validateStockAvailability(101L, 1);
        doThrow(new InsufficientStockException(102L, "Spark Plug RG6HCC", 10, 4))
                .when(inventoryService).validateStockAvailability(102L, 10);

        InsufficientStockException ex = assertThrows(InsufficientStockException.class,
                () -> checkoutValidator.validateStockAvailability(items, inventoryService));

        assertEquals(102L, ex.getProductId());
        assertEquals(10, ex.getRequestedQuantity());
        assertEquals(4, ex.getAvailableQuantity());
    }

    // ──────────────────────────────────────────────
    //  Unavailable / Discontinued Product Scenario (HTTP 404)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Rejects checkout when product ID does not exist or is inactive in catalog")
    void shouldThrowProductNotFoundException_whenProductNotFoundOrDiscontinued() {
        List<SaleItemRequest> items = List.of(createItem(999L, 1));

        doThrow(new ProductNotFoundException(999L))
                .when(inventoryService).validateStockAvailability(999L, 1);

        ProductNotFoundException ex = assertThrows(ProductNotFoundException.class,
                () -> checkoutValidator.validateStockAvailability(items, inventoryService));

        assertEquals(999L, ex.getProductId());
        assertTrue(ex.getMessage().contains("Product with ID 999 was not found or is discontinued"));
    }

    // ──────────────────────────────────────────────
    //  Edge Cases & Precondition Checks
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Rejects null or empty item list during stock check")
    void shouldThrowInvalidCheckoutException_whenItemsListNullOrEmpty() {
        assertThrows(InvalidCheckoutException.class,
                () -> checkoutValidator.validateStockAvailability(null, inventoryService));

        assertThrows(InvalidCheckoutException.class,
                () -> checkoutValidator.validateStockAvailability(Collections.emptyList(), inventoryService));
    }

    @Test
    @DisplayName("Rejects stock validation when InventoryService is null")
    void shouldThrowIllegalArgumentException_whenInventoryServiceIsNull() {
        List<SaleItemRequest> items = List.of(createItem(101L, 2));

        assertThrows(IllegalArgumentException.class,
                () -> checkoutValidator.validateStockAvailability(items, null));
    }

    // ──────────────────────────────────────────────
    //  SalesService Integration & Delegation
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("SalesService delegates stock check to centralized InventoryService")
    void shouldDelegateToInventoryService_viaSalesService() {
        List<SaleItemRequest> items = List.of(createItem(101L, 3));
        doNothing().when(inventoryService).validateStockAvailability(101L, 3);

        assertDoesNotThrow(() -> salesService.validateStockAvailability(items));

        verify(inventoryService).validateStockAvailability(101L, 3);
    }

    @Test
    @DisplayName("SalesService throws IllegalStateException when InventoryService is not wired")
    void shouldThrowIllegalStateException_whenSalesServiceHasNoInventoryService() {
        SalesService unconfiguredService = new SalesService(salesRepository, checkoutValidator, null);
        List<SaleItemRequest> items = List.of(createItem(101L, 1));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> unconfiguredService.validateStockAvailability(items));

        assertTrue(ex.getMessage().contains("InventoryService is not available"));
    }

    // ──────────────────────────────────────────────
    //  Strict Separation of Concerns (No Direct Stock Updates)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Validating stock NEVER invokes modifying methods like deductStock or restoreStock")
    void shouldNeverModifyStock_duringStockValidation() {
        List<SaleItemRequest> items = List.of(createItem(101L, 2));
        doNothing().when(inventoryService).validateStockAvailability(101L, 2);

        checkoutValidator.validateStockAvailability(items, inventoryService);

        // Verify only read-only check was invoked, never deductStock or restoreStock
        verify(inventoryService).validateStockAvailability(101L, 2);
        verify(inventoryService, never()).deductStock(any(), any(), any(), any());
        verify(inventoryService, never()).restoreStock(any(), any(), any(), any());
    }

    // ──────────────────────────────────────────────
    //  Combined Full Checkout Validation
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Full checkout validation passes when data, prices, and stock are valid")
    void shouldPassFullCheckoutValidation_whenDataAndStockValid() {
        List<SaleItemRequest> items = List.of(createItem(101L, 2));
        SaleCreateRequest request = createRequest(null, PaymentMethod.CASH,
                new BigDecimal("500.00"), BigDecimal.ZERO, items);
        BigDecimal authoritativeSubtotal = new BigDecimal("440.00");

        doNothing().when(inventoryService).validateStockAvailability(101L, 2);

        assertDoesNotThrow(() -> salesService.validateCheckoutRequest(request, authoritativeSubtotal, inventoryService));
        verify(inventoryService).validateStockAvailability(101L, 2);
    }

    @Test
    @DisplayName("Full checkout validation fails when stock is insufficient despite valid form data")
    void shouldFailFullCheckoutValidation_whenStockIsInsufficient() {
        List<SaleItemRequest> items = List.of(createItem(101L, 10));
        SaleCreateRequest request = createRequest(null, PaymentMethod.CASH,
                new BigDecimal("2500.00"), BigDecimal.ZERO, items);
        BigDecimal authoritativeSubtotal = new BigDecimal("2200.00");

        doThrow(new InsufficientStockException(101L, "Clutch Plate", 10, 3))
                .when(inventoryService).validateStockAvailability(101L, 10);

        assertThrows(InsufficientStockException.class,
                () -> salesService.validateCheckoutRequest(request, authoritativeSubtotal, inventoryService));
    }

    // ──────────────────────────────────────────────
    //  SalesExceptionHandler Unit Tests
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("SalesExceptionHandler maps InsufficientStockException to HTTP 409 Conflict")
    void shouldMapInsufficientStockException_toHttp409Conflict() {
        when(httpServletRequest.getRequestURI()).thenReturn("/api/v1/sales");

        InsufficientStockException ex = new InsufficientStockException(101L, "Clutch Plate Assembly", 5, 2);
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleInsufficientStock(ex, httpServletRequest);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().status());
        assertEquals("Conflict", response.getBody().error());
        assertTrue(response.getBody().message().contains("Insufficient stock for product ID 101"));
        assertEquals("/api/v1/sales", response.getBody().path());
        assertNotNull(response.getBody().timestamp());
    }

    @Test
    @DisplayName("SalesExceptionHandler maps ProductNotFoundException to HTTP 404 Not Found")
    void shouldMapProductNotFoundException_toHttp404NotFound() {
        when(httpServletRequest.getRequestURI()).thenReturn("/api/v1/sales");

        ProductNotFoundException ex = new ProductNotFoundException(999L);
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleProductNotFound(ex, httpServletRequest);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().status());
        assertEquals("Not Found", response.getBody().error());
        assertTrue(response.getBody().message().contains("Product with ID 999 was not found"));
        assertEquals("/api/v1/sales", response.getBody().path());
        assertNotNull(response.getBody().timestamp());
    }
}
