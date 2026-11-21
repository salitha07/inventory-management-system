package com.bajaj.inventory_backend.dashboard;

import com.bajaj.inventory_backend.dto.dashboard.DashboardSummaryResponse;
import com.bajaj.inventory_backend.dto.dashboard.LowStockAlertResponse;
import com.bajaj.inventory_backend.dto.dashboard.RecentActivityResponse;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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

        return new DashboardSummaryResponse(
                todaySales,
                monthlySales,
                null,
                totalProducts,
                null
        );
    }

    public List<LowStockAlertResponse> getLowStockProducts() {
        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Low-stock alerts are unavailable because the inventory migration is not active."
        );
    }

    public List<RecentActivityResponse> getRecentActivities() {
        return dashboardRepository.getRecentSales();
    }
}
