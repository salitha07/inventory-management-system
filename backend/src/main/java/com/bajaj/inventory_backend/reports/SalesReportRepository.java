
package com.bajaj.inventory_backend.reports;

import com.bajaj.inventory_backend.sales.Sale;
import com.bajaj.inventory_backend.sales.SaleStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;

public interface SalesReportRepository extends Repository<Sale, Long> {

    @Query("""
        SELECT
            COALESCE(SUM(s.total), 0) AS totalSales,
            COALESCE(SUM(s.discount), 0) AS totalDiscounts,
            COUNT(s) AS totalTransactions
        FROM Sale s
        WHERE s.status = :status
        AND s.saleDate >= :fromDate
        AND s.saleDate < :toDate
        """)
    SalesTotals getSalesTotals(
            @Param("status") SaleStatus status,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate
    );

    interface SalesTotals {
        BigDecimal getTotalSales();
        BigDecimal getTotalDiscounts();
        Long getTotalTransactions();
    }
}
