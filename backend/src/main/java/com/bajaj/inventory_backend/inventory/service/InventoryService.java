package com.bajaj.inventory_backend.inventory.service;

import com.bajaj.inventory_backend.common.exception.InsufficientStockException;
import com.bajaj.inventory_backend.common.exception.ProductNotFoundException;
import com.bajaj.inventory_backend.inventory.entity.StockMovementType;

import java.math.BigDecimal;

/**
 * Centralized Inventory Management Service contract (owned by Member 6).
 *
 * <p>Sales module interacts strictly with this interface for stock queries,
 * stock availability validation, and atomic stock deductions.
 * Direct modification of inventory tables by the sales module is strictly prohibited.
 */
public interface InventoryService {

    /**
     * Checks if the requested quantity of a product is available in stock.
     *
     * @param productId product primary key
     * @param requestedQuantity quantity to validate
     * @return true if sufficient stock is on hand
     * @throws ProductNotFoundException if product does not exist or is inactive
     */
    boolean isStockAvailable(Long productId, Integer requestedQuantity);

    /**
     * Retrieves current available on-hand stock quantity.
     *
     * @param productId product primary key
     * @return available stock quantity
     * @throws ProductNotFoundException if product does not exist or is inactive
     */
    Integer getAvailableStock(Long productId);

    /**
     * Validates that stock is available for the given product and quantity.
     *
     * @param productId product primary key
     * @param requestedQuantity quantity required for checkout
     * @throws InsufficientStockException if requestedQuantity > available stock
     * @throws ProductNotFoundException if product does not exist or is inactive
     */
    void validateStockAvailability(Long productId, Integer requestedQuantity);

    /**
     * Atomically validates and decrements stock for a single spare part.
     * Must participate in the caller's active database transaction.
     *
     * @param productId product primary key
     * @param quantity quantity to deduct (>= 1)
     * @param movementType movement classification (SALE, RETURN, etc.)
     * @param referenceId ID of triggering entity (sale.getId())
     * @throws InsufficientStockException if available stock < quantity
     * @throws ProductNotFoundException if product not found or inactive
     */
    void deductStock(
            Long productId,
            Integer quantity,
            StockMovementType movementType,
            Long referenceId
    );

    /**
     * Retrieves the current unit acquisition cost basis of a spare part.
     *
     * @param productId product primary key
     * @return acquisition cost per unit
     * @throws ProductNotFoundException if product not found or inactive
     */
    BigDecimal getCurrentUnitCost(Long productId);

    /**
     * Restores stock during same-day sale voiding or customer returns.
     *
     * @param productId product primary key
     * @param quantity quantity to restore (>= 1)
     * @param movementType movement classification
     * @param referenceId ID of triggering entity
     */
    void restoreStock(
            Long productId,
            Integer quantity,
            StockMovementType movementType,
            Long referenceId
    );
}

