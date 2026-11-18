package com.bajaj.inventory_backend.sales.validation;

import com.bajaj.inventory_backend.sales.PaymentMethod;
import com.bajaj.inventory_backend.sales.dto.SaleCreateRequest;
import com.bajaj.inventory_backend.sales.dto.SaleItemRequest;
import com.bajaj.inventory_backend.sales.exception.InvalidCheckoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CheckoutValidatorTest {

    private CheckoutValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CheckoutValidator();
    }

    private SaleItemRequest createItem(Long productId, Integer quantity) {
        return new SaleItemRequest(productId, quantity);
    }

    private SaleCreateRequest createRequest(Long customerId, PaymentMethod method,
                                            BigDecimal tendered, BigDecimal discount,
                                            List<SaleItemRequest> items) {
        return new SaleCreateRequest(customerId, method, tendered, null, discount, items);
    }

    @Test
    @DisplayName("Valid walk-in cash transaction passes validation")
    void testValidWalkInCashSale_Success() {
        List<SaleItemRequest> items = List.of(createItem(101L, 2));
        SaleCreateRequest request = createRequest(null, PaymentMethod.CASH,
                new BigDecimal("500.00"), BigDecimal.ZERO, items);

        BigDecimal authoritativeSubtotal = new BigDecimal("440.00");

        assertDoesNotThrow(() -> validator.validateCheckout(request, authoritativeSubtotal));
    }

    @Test
    @DisplayName("Valid registered customer credit sale passes validation")
    void testValidRegisteredCreditSale_Success() {
        List<SaleItemRequest> items = List.of(createItem(101L, 2));
        SaleCreateRequest request = createRequest(42L, PaymentMethod.CREDIT,
                null, new BigDecimal("40.00"), items);

        BigDecimal authoritativeSubtotal = new BigDecimal("440.00");

        assertDoesNotThrow(() -> validator.validateCheckout(request, authoritativeSubtotal));
    }

    @Test
    @DisplayName("Walk-in sale with store credit payment is rejected")
    void testWalkInWithCredit_ThrowsException() {
        List<SaleItemRequest> items = List.of(createItem(101L, 1));
        SaleCreateRequest request = createRequest(null, PaymentMethod.CREDIT,
                null, BigDecimal.ZERO, items);

        InvalidCheckoutException ex = assertThrows(InvalidCheckoutException.class,
                () -> validator.validateCheckout(request, new BigDecimal("220.00")));

        assertTrue(ex.getMessage().contains("Customer store credit requires a registered customer account"));
    }

    @Test
    @DisplayName("Invalid or negative customer ID is rejected")
    void testNegativeCustomerId_ThrowsException() {
        List<SaleItemRequest> items = List.of(createItem(101L, 1));
        SaleCreateRequest request = createRequest(-5L, PaymentMethod.CASH,
                new BigDecimal("500.00"), BigDecimal.ZERO, items);

        InvalidCheckoutException ex = assertThrows(InvalidCheckoutException.class,
                () -> validator.validateCheckout(request, new BigDecimal("220.00")));

        assertTrue(ex.getMessage().contains("Customer ID must be a positive integer"));
    }

    @Test
    @DisplayName("Cash sale with tendered amount less than total is rejected")
    void testCashTenderedLessThanTotal_ThrowsException() {
        List<SaleItemRequest> items = List.of(createItem(101L, 2));
        SaleCreateRequest request = createRequest(null, PaymentMethod.CASH,
                new BigDecimal("300.00"), BigDecimal.ZERO, items);

        BigDecimal authoritativeSubtotal = new BigDecimal("440.00");

        InvalidCheckoutException ex = assertThrows(InvalidCheckoutException.class,
                () -> validator.validateCheckout(request, authoritativeSubtotal));

        assertTrue(ex.getMessage().contains("less than the required total payable amount"));
    }

    @Test
    @DisplayName("Discount exceeding subtotal is rejected")
    void testDiscountExceedingSubtotal_ThrowsException() {
        List<SaleItemRequest> items = List.of(createItem(101L, 1));
        SaleCreateRequest request = createRequest(null, PaymentMethod.CARD,
                null, new BigDecimal("250.00"), items);

        BigDecimal authoritativeSubtotal = new BigDecimal("220.00");

        InvalidCheckoutException ex = assertThrows(InvalidCheckoutException.class,
                () -> validator.validateCheckout(request, authoritativeSubtotal));

        assertTrue(ex.getMessage().contains("cannot exceed authoritative subtotal"));
    }

    @Test
    @DisplayName("Negative discount is rejected")
    void testNegativeDiscount_ThrowsException() {
        List<SaleItemRequest> items = List.of(createItem(101L, 1));
        SaleCreateRequest request = createRequest(null, PaymentMethod.CARD,
                null, new BigDecimal("-10.00"), items);

        InvalidCheckoutException ex = assertThrows(InvalidCheckoutException.class,
                () -> validator.validateCheckout(request, new BigDecimal("220.00")));

        assertTrue(ex.getMessage().contains("Discount cannot be negative"));
    }

    @Test
    @DisplayName("Empty or null sale items list is rejected")
    void testEmptyItems_ThrowsException() {
        SaleCreateRequest requestEmpty = createRequest(null, PaymentMethod.CASH,
                new BigDecimal("100.00"), BigDecimal.ZERO, Collections.emptyList());

        assertThrows(InvalidCheckoutException.class,
                () -> validator.validateCheckout(requestEmpty, BigDecimal.ZERO));

        SaleCreateRequest requestNull = createRequest(null, PaymentMethod.CASH,
                new BigDecimal("100.00"), BigDecimal.ZERO, null);

        assertThrows(InvalidCheckoutException.class,
                () -> validator.validateCheckout(requestNull, BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Item with zero or negative quantity is rejected")
    void testZeroQuantityItem_ThrowsException() {
        List<SaleItemRequest> items = List.of(createItem(101L, 0));
        SaleCreateRequest request = createRequest(null, PaymentMethod.CASH,
                new BigDecimal("100.00"), BigDecimal.ZERO, items);

        InvalidCheckoutException ex = assertThrows(InvalidCheckoutException.class,
                () -> validator.validateCheckout(request, new BigDecimal("100.00")));

        assertTrue(ex.getMessage().contains("Quantity must be a positive integer"));
    }

    @Test
    @DisplayName("Item with null or non-positive product ID is rejected")
    void testInvalidProductId_ThrowsException() {
        List<SaleItemRequest> items = List.of(createItem(-1L, 2));
        SaleCreateRequest request = createRequest(null, PaymentMethod.CASH,
                new BigDecimal("100.00"), BigDecimal.ZERO, items);

        InvalidCheckoutException ex = assertThrows(InvalidCheckoutException.class,
                () -> validator.validateCheckout(request, new BigDecimal("100.00")));

        assertTrue(ex.getMessage().contains("Product ID is required and must be a positive integer"));
    }

    @Test
    @DisplayName("Duplicate product IDs in checkout items are rejected")
    void testDuplicateProductIds_ThrowsException() {
        List<SaleItemRequest> items = List.of(
                createItem(101L, 2),
                createItem(101L, 3)
        );
        SaleCreateRequest request = createRequest(null, PaymentMethod.CASH,
                new BigDecimal("1000.00"), BigDecimal.ZERO, items);

        InvalidCheckoutException ex = assertThrows(InvalidCheckoutException.class,
                () -> validator.validateCheckout(request, new BigDecimal("1000.00")));

        assertTrue(ex.getMessage().contains("Duplicate product ID 101"));
    }

    @Test
    @DisplayName("Zero-trust price policy: Client submitted price hints are ignored, authoritative total enforced")
    void testZeroTrustPrice_AuthoritativeTotalEnforced() {
        // Client sends fake unit selling price of 1.00 for a 220.00 product
        SaleItemRequest tamperedItem = new SaleItemRequest(101L, 1, new BigDecimal("1.00"));
        // Client sends tendered amount of 1.00
        SaleCreateRequest request = createRequest(null, PaymentMethod.CASH,
                new BigDecimal("1.00"), BigDecimal.ZERO, List.of(tamperedItem));

        // Server authoritative subtotal is 220.00
        BigDecimal authoritativeSubtotal = new BigDecimal("220.00");

        // Server rejects checkout because 1.00 < 220.00 authoritative total
        InvalidCheckoutException ex = assertThrows(InvalidCheckoutException.class,
                () -> validator.validateCheckout(request, authoritativeSubtotal));

        assertTrue(ex.getMessage().contains("less than the required total payable amount (220.00)"));
    }

    @Test
    @DisplayName("QR Payment method (LankaQR) is accepted for valid checkout")
    void testValidQrPayment_Success() {
        List<SaleItemRequest> items = List.of(createItem(101L, 1));
        SaleCreateRequest request = createRequest(null, PaymentMethod.QR_PAYMENT,
                null, BigDecimal.ZERO, items);

        BigDecimal authoritativeSubtotal = new BigDecimal("220.00");

        assertDoesNotThrow(() -> validator.validateCheckout(request, authoritativeSubtotal));
    }

    @Test
    @DisplayName("Legacy UPI payment method is preserved and valid for historical/backwards compatibility")
    @SuppressWarnings("deprecation")
    void testLegacyUpiPayment_Success() {
        List<SaleItemRequest> items = List.of(createItem(101L, 1));
        SaleCreateRequest request = createRequest(null, PaymentMethod.UPI,
                null, BigDecimal.ZERO, items);

        BigDecimal authoritativeSubtotal = new BigDecimal("220.00");

        assertDoesNotThrow(() -> validator.validateCheckout(request, authoritativeSubtotal));
    }
}

