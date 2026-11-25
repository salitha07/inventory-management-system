
package com.bajaj.inventory_backend.reports;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportsController {

    @GetMapping("/sales")
    public String salesReport() {
        return "Sales report - implementation pending";
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
