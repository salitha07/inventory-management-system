package com.bajaj.inventory_backend.inventory.persistence.jpa;

import com.bajaj.inventory_backend.inventory.persistence.StockPersistenceException;
import com.bajaj.inventory_backend.inventory.persistence.StockPersistenceException.Reason;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaStockInitializer {
    public enum Result { INITIALIZED, ALREADY_INITIALIZED }

    private final InventoryStockRepository stock;

    JpaStockInitializer(InventoryStockRepository stock) {
        this.stock = stock;
    }

    @Transactional
    public Result initializeStock(Long productId) {
        JpaStockAccess.requireProductId(productId);
        JpaStockAccess.requireWritableTransaction();
        stock.flush();
        JpaStockAccess.lockProduct(stock, productId);
        return switch (stock.insertEmptyStock(productId)) {
            case 1 -> Result.INITIALIZED;
            case 0 -> Result.ALREADY_INITIALIZED;
            default -> throw new StockPersistenceException(Reason.UNEXPECTED_ROW_COUNT);
        };
    }
}
