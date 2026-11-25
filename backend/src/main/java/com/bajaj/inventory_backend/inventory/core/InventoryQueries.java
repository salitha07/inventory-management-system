package com.bajaj.inventory_backend.inventory.core;

import com.bajaj.inventory_backend.inventory.dto.InventoryContracts.*;
import org.springframework.data.domain.Page;

/**
 * Read-only Phase 01 stock and movement queries.
 * Reads are snapshots and never reserve stock or initialize missing balances.
 */
public interface InventoryQueries {
    /**
     * Require a positive Product ID. Missing Product and STOCK_NOT_INITIALIZED are distinct
     * failures; an initialized quantity of zero is a valid result. Never fabricate zero.
     * Exact shared exception mapping remains subject to integration agreement.
     */
    CurrentStockView getCurrentStock(Long productId);

    /**
     * Optional positive productId filters by exact Product; null means all matching Products.
     * query/page are required; use existing PageQuery bounds (zero-based, size 1..100).
     * Query text matches Product code/name case-insensitively; category/active are optional.
     * lowStockOnly means quantity <= reorderLevel, including equality and 0/0. Null threshold
     * means unconfigured, never zero, and is excluded when lowStockOnly is true.
     * CurrentStockView has no lowStock field; callers derive status only for non-null thresholds.
     * Include only initialized balances; absent rows are not known-zero snapshots. Use detail
     * lookup to distinguish missing Product from uninitialized stock. No extra status DTO added.
     * Stable order: productCode ascending, then productId ascending.
     */
    Page<CurrentStockView> searchCurrentStock(Long productId, CurrentStockQuery query, PageQuery page);

    /**
     * Required query/page; optional Product/type/reference/actor filters are in the DTO.
     * Time interval is inclusive-from/exclusive-to. Stable order: createdAt DESC, movementId DESC.
     * Empty history does not establish whether a Product has an initialized balance.
     */
    Page<StockMovementView> searchStockMovements(StockMovementQuery query, PageQuery page);
}
