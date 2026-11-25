package com.bajaj.inventory_backend.sales;

/**
 * Supported payment methods for POS sales transactions.
 */
public enum PaymentMethod {
    CASH,
    CARD,
    QR_PAYMENT,
    BANK_TRANSFER,
    CREDIT,
    /**
     * Legacy payment method preserved for backwards compatibility with historical sales.
     */
    @Deprecated
    UPI
}

