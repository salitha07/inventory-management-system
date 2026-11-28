
package com.bajaj.inventory_backend.reports;

import com.bajaj.inventory_backend.sales.SaleStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@Transactional(readOnly = true)
public class SalesReportService {

    private static final ZoneId SRI_LANKA_ZONE =
            ZoneId.of("Asia/Colombo");

    private final SalesReportRepository salesReportRepository;

    public SalesReportService(
            SalesReportRepository salesReportRepository) {
        this.salesReportRepository = salesReportRepository;
    }

    public SalesReportResponse getSalesReport(
            LocalDate fromDate,
            LocalDate toDate) {

        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException(
                    "Both fromDate and toDate are required");
        }

        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException(
                    "fromDate cannot be after toDate");
        }

        var fromDateTime = fromDate
                .atStartOfDay(SRI_LANKA_ZONE)
                .toInstant();

        var toDateTime = toDate
                .plusDays(1)
                .atStartOfDay(SRI_LANKA_ZONE)
                .toInstant();

        var totals = salesReportRepository.getSalesTotals(
                SaleStatus.COMPLETED,
                fromDateTime,
                toDateTime
        );

        return new SalesReportResponse(
                fromDate,
                toDate,
                totals.getTotalSales(),
                totals.getTotalDiscounts(),
                totals.getTotalTransactions()
        );
    }
}
