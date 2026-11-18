
package com.bajaj.inventory_backend.dto.dashboard;

import java.math.BigDecimal;

public class DashboardSummaryResponse {

    private BigDecimal todaySales;
    private BigDecimal monthlySales;
    private BigDecimal purchaseSummary;
    private long totalProducts;
    private long lowStockCount;

    public DashboardSummaryResponse(
            BigDecimal todaySales,
            BigDecimal monthlySales,
            BigDecimal purchaseSummary,
            long totalProducts,
            long lowStockCount) {

        this.todaySales = todaySales;
        this.monthlySales = monthlySales;
        this.purchaseSummary = purchaseSummary;
        this.totalProducts = totalProducts;
        this.lowStockCount = lowStockCount;
    }

    public BigDecimal getTodaySales() {
        return todaySales;
    }

    public BigDecimal getMonthlySales() {
        return monthlySales;
    }

    public BigDecimal getPurchaseSummary() {
        return purchaseSummary;
    }

    public long getTotalProducts() {
        return totalProducts;
    }

    public long getLowStockCount() {
        return lowStockCount;
    }
}
