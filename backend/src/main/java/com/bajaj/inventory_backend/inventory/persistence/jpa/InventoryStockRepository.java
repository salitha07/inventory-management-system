package com.bajaj.inventory_backend.inventory.persistence.jpa;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

interface InventoryStockRepository extends JpaRepository<InventoryStock, Long> {
    String STOCK_COLUMNS = """
            SELECT p.id AS productId, p.productCode AS productCode, p.productName AS productName,
                   p.category.id AS categoryId, p.active AS active, s.quantity AS quantity,
                   s.reorderLevel AS reorderLevel
            """;

    interface StockRow {
        Long getProductId();
        String getProductCode();
        String getProductName();
        Long getCategoryId();
        boolean getActive();
        Integer getQuantity();
        Integer getReorderLevel();
    }

    @Query(value = "SELECT id FROM products WHERE id = :id FOR KEY SHARE", nativeQuery = true)
    Optional<Long> lockProduct(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM InventoryStock s WHERE s.productId = :id")
    Optional<InventoryStock> lockStock(@Param("id") Long id);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO inventory_stock (product_id, quantity, reorder_level)
            VALUES (:id, 0, NULL) ON CONFLICT (product_id) DO NOTHING
            """, nativeQuery = true)
    int insertEmptyStock(@Param("id") Long id);

    @Query(STOCK_COLUMNS + " FROM Product p LEFT JOIN InventoryStock s ON s.productId = p.id WHERE p.id = :id")
    Optional<StockRow> findStockSnapshot(@Param("id") Long id);

    String SEARCH_FILTERS = """
             FROM InventoryStock s JOIN Product p ON p.id = s.productId
             WHERE (:id IS NULL OR p.id = :id)
               AND (:category IS NULL OR p.category.id = :category)
               AND (:active IS NULL OR p.active = :active)
               AND (p.productCode ILIKE :pattern ESCAPE '!' OR p.productName ILIKE :pattern ESCAPE '!')
               AND (:lowStock = false OR (s.reorderLevel IS NOT NULL AND s.quantity <= s.reorderLevel))
            """;

    @Query(value = STOCK_COLUMNS + SEARCH_FILTERS + " ORDER BY p.productCode ASC, p.id ASC",
            countQuery = "SELECT count(s)" + SEARCH_FILTERS)
    Page<StockRow> search(@Param("id") Long id, @Param("category") Long category,
            @Param("active") Boolean active, @Param("pattern") String pattern,
            @Param("lowStock") boolean lowStock, Pageable page);
}
