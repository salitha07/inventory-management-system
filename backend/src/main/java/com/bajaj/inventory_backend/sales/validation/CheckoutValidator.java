package com.bajaj.inventory_backend.sales.validation;

import com.bajaj.inventory_backend.common.exception.InsufficientStockException;
import com.bajaj.inventory_backend.common.exception.ProductNotFoundException;
import com.bajaj.inventory_backend.inventory.service.InventoryService;
import com.bajaj.inventory_backend.sales.PaymentMethod;
import com.bajaj.inventory_backend.sales.dto.SaleCreateRequest;
import com.bajaj.inventory_backend.sales.dto.SaleItemRequest;
import com.bajaj.inventory_backend.sales.exception.InvalidCheckoutException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Dedicated business validator for checkout transactions.
 *
 * <p>Enforces zero-trust price validation, mandatory item integrity,
 * customer-tender compatibility, and discount bounds.
 */
@Component
public class CheckoutValidator {

    /**
     * Authoritatively validates a checkout request against business invariants.
     *
     * <p>ZERO-TRUST PRINCIPLE:
     * Client-submitted prices are ignored. The {@code authoritativeSubtotal}
     * must be calculated by the server using official catalog product prices.
     *
     * @param request the client checkout request
     * @param authoritativeSubtotal the server-calculated subtotal
     * @throws InvalidCheckoutException if any business invariant is violated
     */
    public void validateCheckout(SaleCreateRequest request, BigDecimal authoritativeSubtotal) {
        if (request == null) {
            throw new InvalidCheckoutException("Checkout request cannot be null");
        }

        // 1. Validate items
        validateItems(request.getItems());

        // 2. Validate payment method
        if (request.getPaymentMethod() == null) {
            throw new InvalidCheckoutException("Payment method is required");
        }

        // 3. Validate optional customer reference & customer-tender rules
        if (request.getCustomerId() != null && request.getCustomerId() <= 0) {
            throw new InvalidCheckoutException("Customer ID must be a positive integer");
        }

        if (request.getPaymentMethod() == PaymentMethod.CREDIT && request.getCustomerId() == null) {
            throw new InvalidCheckoutException(
                    "Customer store credit requires a registered customer account (cannot be walk-in)");
        }

        // 4. Validate discount limits
        BigDecimal discount = request.getDiscount() != null ? request.getDiscount() : BigDecimal.ZERO;
        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidCheckoutException("Discount cannot be negative: " + discount);
        }

        if (authoritativeSubtotal != null) {
            if (authoritativeSubtotal.compareTo(BigDecimal.ZERO) < 0) {
                throw new InvalidCheckoutException("Subtotal cannot be negative");
            }
            if (discount.compareTo(authoritativeSubtotal) > 0) {
                throw new InvalidCheckoutException(String.format(
                        "Discount (%s) cannot exceed authoritative subtotal (%s)",
                        discount, authoritativeSubtotal));
            }
        }

        // 5. Authoritatively calculate total payable (subtotal - discount)
        BigDecimal netTotal = authoritativeSubtotal != null
                ? authoritativeSubtotal.subtract(discount)
                : BigDecimal.ZERO;

        // 6. Validate cash amount tendered
        if (request.getPaymentMethod() == PaymentMethod.CASH) {
            if (request.getAmountTendered() == null) {
                throw new InvalidCheckoutException("Amount tendered is required for CASH payment");
            }
            if (request.getAmountTendered().compareTo(BigDecimal.ZERO) < 0) {
                throw new InvalidCheckoutException("Amount tendered cannot be negative");
            }
            if (request.getAmountTendered().compareTo(netTotal) < 0) {
                throw new InvalidCheckoutException(String.format(
                        "Amount tendered (%s) is less than the required total payable amount (%s)",
                        request.getAmountTendered(), netTotal));
            }
        }
    }

    /**
     * Authoritatively validates a checkout request including stock availability checks.
     *
     * @param request the client checkout request
     * @param authoritativeSubtotal the server-calculated subtotal
     * @param inventoryService the centralized inventory service contract
     * @throws InvalidCheckoutException if any business invariant is violated
     * @throws InsufficientStockException if requested stock exceeds availability
     * @throws ProductNotFoundException if any item refers to a non-existent part
     */
    public void validateCheckout(SaleCreateRequest request, BigDecimal authoritativeSubtotal, InventoryService inventoryService) {
        validateCheckout(request, authoritativeSubtotal);
        if (inventoryService != null && request != null) {
            validateStockAvailability(request.getItems(), inventoryService);
        }
    }

    /**
     * Validates stock availability for each item in the sale request against
     * the centralized inventory service.
     *
     * <p>SEPARATION OF CONCERNS & AUDIT INTEGRITY:
     * Sales module does NOT update or alter inventory tables directly.
     * Inventory business rules and availability validation remain strictly owned
     * by {@link InventoryService}.
     *
     * @param items list of line items
     * @param inventoryService the centralized inventory service contract
     * @throws InsufficientStockException if requested quantity exceeds available stock
     * @throws ProductNotFoundException if product does not exist or is inactive
     * @throws InvalidCheckoutException if items list is null or empty
     */
    public void validateStockAvailability(List<SaleItemRequest> items, InventoryService inventoryService) {
        if (inventoryService == null) {
            throw new IllegalArgumentException("InventoryService cannot be null");
        }
        if (items == null || items.isEmpty()) {
            throw new InvalidCheckoutException("Sale must contain at least one item");
        }

        // 1. Initial integrity and null checks
        for (int i = 0; i < items.size(); i++) {
            SaleItemRequest item = items.get(i);
            if (item == null) {
                throw new InvalidCheckoutException(String.format("Item at index %d cannot be null", i));
            }
            if (item.getProductId() == null || item.getProductId() <= 0) {
                throw new InvalidCheckoutException(String.format(
                        "Product ID is required and must be a positive integer for item at index %d", i));
            }
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new InvalidCheckoutException(String.format(
                        "Quantity must be a positive integer (at least 1) for product ID %d", item.getProductId()));
            }
        }

        // 2. Sort items by productId ascending for deterministic lock ordering and deadlock prevention
        List<SaleItemRequest> sortedItems = items.stream()
                .sorted(Comparator.comparing(SaleItemRequest::getProductId))
                .toList();

        // 3. Centralized stock availability check (owned by Member 6) in ascending key order
        for (SaleItemRequest item : sortedItems) {
            inventoryService.validateStockAvailability(item.getProductId(), item.getQuantity());
        }
    }

    /**
     * Validates item list and individual items for product ID and positive quantity.
     *
     * @param items list of line item requests
     * @throws InvalidCheckoutException if items list or any item is invalid
     */
    public void validateItems(List<SaleItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new InvalidCheckoutException("Sale must contain at least one item");
        }

        Set<Long> seenProductIds = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            SaleItemRequest item = items.get(i);
            if (item == null) {
                throw new InvalidCheckoutException(String.format("Item at index %d cannot be null", i));
            }

            if (item.getProductId() == null || item.getProductId() <= 0) {
                throw new InvalidCheckoutException(String.format(
                        "Product ID is required and must be a positive integer for item at index %d", i));
            }

            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new InvalidCheckoutException(String.format(
                        "Quantity must be a positive integer (at least 1) for product ID %d", item.getProductId()));
            }

            if (!seenProductIds.add(item.getProductId())) {
                throw new InvalidCheckoutException(String.format(
                        "Duplicate product ID %d found in checkout items. Quantities must be aggregated into a single line item.",
                        item.getProductId()));
            }
        }
    }
}

