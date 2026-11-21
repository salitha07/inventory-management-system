
package com.bajaj.inventory_backend.dashboard;

import com.bajaj.inventory_backend.dto.dashboard.DashboardSummaryResponse;
import com.bajaj.inventory_backend.dto.dashboard.LowStockAlertResponse;
import com.bajaj.inventory_backend.dto.dashboard.RecentActivityResponse;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class DashboardService {

    private final DashboardRepository dashboardRepository;

    public DashboardService(DashboardRepository dashboardRepository) {
        this.dashboardRepository = dashboardRepository;
    }

    public DashboardSummaryResponse getDashboardSummary() {

        BigDecimal todaySales = dashboardRepository.getTodaySales();
        BigDecimal monthlySales = dashboardRepository.getMonthlySales();

        long totalProducts = dashboardRepository.getTotalProducts();
        long lowStockCount = dashboardRepository.getLowStockCount();

        return new DashboardSummaryResponse(
                todaySales,
                monthlySales,
                BigDecimal.ZERO,
                totalProducts,
                lowStockCount
        );
    }

    public List<LowStockAlertResponse> getLowStockProducts() {
        return dashboardRepository.getLowStockProducts();
    }

    public List<RecentActivityResponse> getRecentActivities() {
        return dashboardRepository.getRecentSales();
    }
}
