package com.bajaj.inventory_backend.controller;

import com.bajaj.inventory_backend.dashboard.DashboardService;
import com.bajaj.inventory_backend.dto.dashboard.DashboardSummaryResponse;
import com.bajaj.inventory_backend.dto.dashboard.LowStockAlertResponse;
import com.bajaj.inventory_backend.dto.dashboard.RecentActivityResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> getDashboardSummary() {
        return ResponseEntity.ok(
                dashboardService.getDashboardSummary()
        );
    }

    @GetMapping("/low-stock")
    public ResponseEntity<List<LowStockAlertResponse>> getLowStockProducts() {
        return ResponseEntity.ok(
                dashboardService.getLowStockProducts()
        );
    }

    @GetMapping("/recent-activity")
    public ResponseEntity<List<RecentActivityResponse>> getRecentActivities() {
        return ResponseEntity.ok(
                dashboardService.getRecentActivities()
        );
    }
}