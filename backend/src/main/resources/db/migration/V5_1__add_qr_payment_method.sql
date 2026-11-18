-- ============================================================================
-- Migration: V5_1__add_qr_payment_method.sql
-- Module:    Sales Management & Point of Sale (POS) - Member 5
-- Task:      Support QR_PAYMENT (LankaQR) while preserving legacy UPI
-- ============================================================================

ALTER TABLE sales DROP CONSTRAINT IF EXISTS sales_payment_method_check;
ALTER TABLE sales DROP CONSTRAINT IF EXISTS ck_sales_payment_method;

ALTER TABLE sales ADD CONSTRAINT ck_sales_payment_method CHECK (
    payment_method IN ('CASH', 'CARD', 'QR_PAYMENT', 'UPI', 'BANK_TRANSFER', 'CREDIT')
);
