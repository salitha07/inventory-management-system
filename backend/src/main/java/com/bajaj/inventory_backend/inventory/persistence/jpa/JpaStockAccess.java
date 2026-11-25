package com.bajaj.inventory_backend.inventory.persistence.jpa;

import com.bajaj.inventory_backend.inventory.persistence.StockPersistenceException;
import com.bajaj.inventory_backend.inventory.persistence.StockPersistenceException.Reason;
import org.springframework.transaction.support.TransactionSynchronizationManager;

final class JpaStockAccess {
    private JpaStockAccess() { }

    static void requireProductId(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("productId must be positive");
    }

    static void requireWritableTransaction() {
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new StockPersistenceException(Reason.READ_ONLY);
        }
    }

    static void lockProduct(InventoryStockRepository stock, Long id) {
        stock.lockProduct(id).orElseThrow(() -> new StockPersistenceException(Reason.PRODUCT_NOT_FOUND));
    }
}
