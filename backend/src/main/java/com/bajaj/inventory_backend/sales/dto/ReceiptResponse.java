package com.bajaj.inventory_backend.sales.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Formatted receipt response DTO corresponding to the approved POS API contract (GET /api/v1/sales/{id}/receipt).
 * Designed for 80 mm thermal receipt rendering and customer invoices.
 */
public record ReceiptResponse(
        StoreInfo storeInfo,
        ReceiptInfo receiptInfo,
        List<ReceiptItem> items,
        ReceiptTotals totals,
        ReceiptPayment payment,
        String footerNote
) {
    public record StoreInfo(
            String storeName,
            String address,
            String phone,
            String gstin
    ) {}

    public record ReceiptInfo(
            String saleNumber,
            Instant saleDate,
            String cashierName,
            String customerName,
            String customerPhone
    ) {}

    public record ReceiptItem(
            String partNumber,
            String description,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal
    ) {}

    public record ReceiptTotals(
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal taxIncluded,
            BigDecimal grandTotal
    ) {}

    public record ReceiptPayment(
            String paymentMethod,
            BigDecimal amountTendered,
            BigDecimal changeDue,
            String reference
    ) {}
}

