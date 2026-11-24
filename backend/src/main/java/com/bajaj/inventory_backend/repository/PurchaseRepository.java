package com.bajaj.inventory_backend.repository;

import com.bajaj.inventory_backend.purchase.Purchase;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import com.bajaj.inventory_backend.purchase.PurchaseStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

public interface PurchaseRepository
        extends JpaRepository<Purchase, Long> {

    Optional<Purchase> findByPurchaseNumber(String purchaseNumber);
    @Query("""
    SELECT p FROM Purchase p
    WHERE (:purchaseNumber IS NULL
        OR LOWER(p.purchaseNumber)
        LIKE LOWER(CONCAT('%', :purchaseNumber, '%')))
    AND (:supplierId IS NULL OR p.supplierId = :supplierId)
    AND (:status IS NULL OR p.status = :status)
    AND (CAST(:fromDate AS date) IS NULL
        OR p.purchaseDate >= :fromDate)
    AND (CAST(:toDate AS date) IS NULL
        OR p.purchaseDate <= :toDate)
    ORDER BY p.purchaseDate DESC, p.id DESC
    """)
    List<Purchase> searchPurchases(
            @Param("purchaseNumber") String purchaseNumber,
            @Param("supplierId") Long supplierId,
            @Param("status") PurchaseStatus status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

}