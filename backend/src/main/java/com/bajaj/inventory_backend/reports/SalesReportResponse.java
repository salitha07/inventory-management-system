
package com.bajaj.inventory_backend.reports;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SalesReportResponse(
        LocalDate fromDate,
        LocalDate toDate,
        BigDecimal totalSales,
        BigDecimal totalDiscounts,
        long totalTransactions
) {
}
