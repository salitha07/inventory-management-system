package com.bajaj.inventory_backend.inventory.persistence.jpa;

import com.bajaj.inventory_backend.inventory.core.InventoryQueries;
import com.bajaj.inventory_backend.inventory.dto.InventoryContracts.*;
import com.bajaj.inventory_backend.inventory.persistence.StockPersistenceException;
import com.bajaj.inventory_backend.inventory.persistence.StockPersistenceException.Reason;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
class JpaInventoryQueries implements InventoryQueries {
    private final InventoryStockRepository stock;
    private final StockMovementRepository movements;

    JpaInventoryQueries(InventoryStockRepository stock, StockMovementRepository movements) {
        this.stock = stock;
        this.movements = movements;
    }

    @Override
    public CurrentStockView getCurrentStock(Long productId) {
        JpaStockAccess.requireProductId(productId);
        return stock.findStockSnapshot(productId)
                .map(JpaInventoryQueries::stockView)
                .orElseThrow(() -> new StockPersistenceException(Reason.PRODUCT_NOT_FOUND));
    }

    @Override
    public Page<CurrentStockView> searchCurrentStock(Long productId, CurrentStockQuery query, PageQuery page) {
        if (productId != null) JpaStockAccess.requireProductId(productId);
        String text = query.text();
        // Both Product search columns are non-null, so "%" also covers a missing search term.
        String pattern = text == null || text.isBlank() ? "%"
                : "%" + text.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return stock.search(productId, query.categoryId(), query.active(), pattern, query.lowStockOnly(),
                PageRequest.of(page.page(), page.size())).map(JpaInventoryQueries::stockView);
    }

    @Override
    public Page<StockMovementView> searchStockMovements(StockMovementQuery query, PageQuery page) {
        Specification<StockMovement> filters = (root, criteria, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (query.productId() != null) predicates.add(cb.equal(root.get("productId"), query.productId()));
            if (query.movementType() != null) predicates.add(cb.equal(root.get("movementType"), query.movementType()));
            if (query.referenceType() != null) predicates.add(cb.equal(root.get("referenceType"), query.referenceType()));
            if (query.referenceId() != null) predicates.add(cb.equal(root.get("referenceId"), query.referenceId()));
            if (query.createdBy() != null) predicates.add(cb.equal(root.get("createdBy"), query.createdBy()));
            if (query.from() != null) predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), query.from()));
            if (query.to() != null) predicates.add(cb.lessThan(root.<Instant>get("createdAt"), query.to()));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return movements.findAll(filters, PageRequest.of(page.page(), page.size(),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"))).map(StockMovement::view);
    }

    private static CurrentStockView stockView(InventoryStockRepository.StockRow row) {
        if (row.getQuantity() == null) throw new StockPersistenceException(Reason.STOCK_NOT_INITIALIZED);
        return new CurrentStockView(row.getProductId(), row.getProductCode(), row.getProductName(),
                row.getCategoryId(), row.getActive(), row.getQuantity(), row.getReorderLevel());
    }
}
