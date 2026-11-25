package com.bajaj.inventory_backend.inventory.persistence.jpa;

import com.bajaj.inventory_backend.inventory.core.InventoryPorts.AtomicStockStore;
import com.bajaj.inventory_backend.inventory.core.InventoryPorts.MovementWrite;
import com.bajaj.inventory_backend.inventory.dto.InventoryContracts.StockMovementView;
import com.bajaj.inventory_backend.inventory.persistence.StockPersistenceException;
import com.bajaj.inventory_backend.inventory.persistence.StockPersistenceException.Reason;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Objects;
import java.util.function.IntFunction;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class JpaAtomicStockStore implements AtomicStockStore {
    private final InventoryStockRepository stock;
    private final StockMovementRepository movements;
    @PersistenceContext
    private EntityManager entityManager;

    JpaAtomicStockStore(InventoryStockRepository stock, StockMovementRepository movements) {
        this.stock = stock;
        this.movements = movements;
    }

    @Override
    @Transactional
    public StockMovementView apply(Long productId, IntFunction<MovementWrite> planner) {
        JpaStockAccess.requireProductId(productId);
        Objects.requireNonNull(planner, "planner");
        JpaStockAccess.requireWritableTransaction();
        // Expose a newly persisted Product to the native identity lock.
        entityManager.flush();
        JpaStockAccess.lockProduct(stock, productId);
        InventoryStock balance = stock.lockStock(productId)
                .orElseThrow(() -> new StockPersistenceException(Reason.STOCK_NOT_INITIALIZED));
        // A locking query can return an entity already managed by the caller.
        entityManager.refresh(balance);
        int before = balance.quantity();
        if (before < 0) throw new StockPersistenceException(Reason.INVALID_WRITE_PLAN);
        MovementWrite write = planner.apply(before);
        if (write == null || !productId.equals(write.productId())
                || write.kind().quantityAfter(before, write.change()) != write.quantityAfter()) {
            throw new StockPersistenceException(Reason.INVALID_WRITE_PLAN);
        }
        String type = write.context().referenceType();
        if (type.codePointCount(0, type.length()) > 64) {
            throw new IllegalArgumentException("referenceType exceeds V6 length 64");
        }

        balance.applyBalance(write.quantityAfter(), write.createdAt());
        StockMovement movement = movements.saveAndFlush(new StockMovement(write));
        entityManager.refresh(movement);
        return movement.view();
    }
}
