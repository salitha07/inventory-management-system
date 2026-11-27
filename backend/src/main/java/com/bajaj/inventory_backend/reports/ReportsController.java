
package com.bajaj.inventory_backend.reports;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportsController {

    private final SalesReportService salesReportService;

    public ReportsController(SalesReportService salesReportService) {
        this.salesReportService = salesReportService;
    }

    @GetMapping("/sales")
    public SalesReportResponse salesReport(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate) {

        return salesReportService.getSalesReport(fromDate, toDate);
    }

    @GetMapping("/purchases")
    public String purchasesReport() {
        return "Purchases report - implementation pending";
    }

    @GetMapping("/inventory")
    public String inventoryReport() {
        return "Inventory report - implementation pending";
    }

    @GetMapping("/profit")
    public String profitReport() {
        return "Profit report - implementation pending";
    }
}
