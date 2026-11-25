package com.bajaj.inventory_backend.inventory.service;

import com.bajaj.inventory_backend.common.exception.InsufficientStockException;
import com.bajaj.inventory_backend.common.exception.ProductNotFoundException;
import com.bajaj.inventory_backend.inventory.core.InventoryOperations;
import com.bajaj.inventory_backend.inventory.core.InventoryQueries;
import com.bajaj.inventory_backend.inventory.dto.InventoryContracts.CurrentStockView;
import com.bajaj.inventory_backend.inventory.dto.InventoryContracts.MovementContext;
import com.bajaj.inventory_backend.inventory.dto.InventoryContracts.StockChangeCommand;
import com.bajaj.inventory_backend.inventory.entity.StockMovementType;
import com.bajaj.inventory_backend.inventory.persistence.StockPersistenceException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.Objects;

/** Register after the Sales movement metadata, actor policy and unit-cost source are agreed. */
final class SalesInventoryService implements InventoryService {
    private final InventoryQueries queries;
    private final InventoryOperations operations;
    private final SalesContext salesContext;
    private final SalesStockActorResolver actors;

    SalesInventoryService(InventoryQueries queries, InventoryOperations operations, SalesContext salesContext,
                          SalesStockActorResolver actors) {
        this.queries = Objects.requireNonNull(queries);
        this.operations = Objects.requireNonNull(operations);
        this.salesContext = Objects.requireNonNull(salesContext);
        this.actors = Objects.requireNonNull(actors);
    }

    interface SalesContext {
        /** Resolve approved deduction metadata and actor from trusted server-side state. */
        MovementContext deductionContext(Long saleId);

        BigDecimal currentUnitCost(Long productId);
    }

    @Override
    public boolean isStockAvailable(Long productId, Integer requestedQuantity) {
        requirePositiveQuantity(requestedQuantity);
        return getAvailableStock(productId) >= requestedQuantity;
    }

    @Override
    public Integer getAvailableStock(Long productId) {
        return activeStock(productId).quantity();
    }

    @Override
    public void validateStockAvailability(Long productId, Integer requestedQuantity) {
        requirePositiveQuantity(requestedQuantity);
        CurrentStockView stock = activeStock(productId);
        if (stock.quantity() < requestedQuantity) {
            throw new InsufficientStockException(productId, stock.productName(), requestedQuantity, stock.quantity());
        }
    }

    @Override
    public void deductStock(Long productId, Integer quantity, StockMovementType movementType, Long referenceId) {
        requirePositiveQuantity(quantity);
        if (movementType != StockMovementType.SALE || referenceId == null || referenceId <= 0) {
            throw new IllegalArgumentException("Sale deduction requires SALE and a positive sale ID");
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("Sale deduction requires the caller's write transaction");
        }
        var actor = actors.requireCheckoutActor();
        MovementContext context = Objects.requireNonNull(salesContext.deductionContext(referenceId),
                "Missing sale movement context");
        if (!referenceId.equals(context.referenceId())) {
            throw new IllegalStateException("Deduction context must reference the calling sale");
        }
        if (!actor.equals(context.actor())) {
            throw new AccessDeniedException("Sale movement actor does not match the authenticated user");
        }
        CurrentStockView stock = activeStock(productId);
        try {
            operations.deductStock(new StockChangeCommand(productId, quantity,
                    new MovementContext(context.referenceType(), context.referenceId(), context.reason(), actor)));
        } catch (InventoryOperations.InsufficientStock ex) {
            throw new InsufficientStockException(productId, stock.productName(), quantity, ex.available());
        } catch (StockPersistenceException ex) {
            throw mapMissingProduct(productId, ex);
        }
    }

    @Override
    public BigDecimal getCurrentUnitCost(Long productId) {
        activeStock(productId);
        return Objects.requireNonNull(salesContext.currentUnitCost(productId), "Missing acquisition cost");
    }

    @Override
    public void restoreStock(Long productId, Integer quantity, StockMovementType movementType, Long referenceId) {
        throw new UnsupportedOperationException("Stock restoration requires approved return or void semantics");
    }

    private CurrentStockView activeStock(Long productId) {
        CurrentStockView stock;
        try {
            stock = queries.getCurrentStock(productId);
        } catch (StockPersistenceException ex) {
            throw mapMissingProduct(productId, ex);
        }
        if (!stock.active()) {
            throw new ProductNotFoundException(productId);
        }
        return stock;
    }

    private RuntimeException mapMissingProduct(Long productId, StockPersistenceException ex) {
        if (ex.reason() == StockPersistenceException.Reason.PRODUCT_NOT_FOUND) {
            return new ProductNotFoundException(productId);
        }
        return ex;
    }

    private void requirePositiveQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }
}
