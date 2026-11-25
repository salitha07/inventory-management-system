package com.bajaj.inventory_backend.sales.dto;

import com.bajaj.inventory_backend.sales.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;

/**
 * Checkout request DTO corresponding to the approved API contract (POST /api/v1/sales).
 */
public class SaleCreateRequest {

    @Positive(message = "Customer ID must be a positive integer")
    private Long customerId; // null represents Walk-in customer

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    @DecimalMin(value = "0.00", inclusive = true, message = "Amount tendered cannot be negative")
    @Digits(integer = 10, fraction = 2, message = "Amount tendered exceeds precision limits")
    private BigDecimal amountTendered;

    private String paymentReference;

    @NotNull(message = "Discount is required")
    @DecimalMin(value = "0.00", inclusive = true, message = "Discount cannot be negative")
    @Digits(integer = 10, fraction = 2, message = "Discount exceeds precision limits")
    private BigDecimal discount = BigDecimal.ZERO;

    @NotEmpty(message = "Sale must contain at least one item")
    private List<@Valid SaleItemRequest> items;

    public SaleCreateRequest() {
    }

    public SaleCreateRequest(Long customerId, PaymentMethod paymentMethod, BigDecimal amountTendered,
                             String paymentReference, BigDecimal discount, List<SaleItemRequest> items) {
        this.customerId = customerId;
        this.paymentMethod = paymentMethod;
        this.amountTendered = amountTendered;
        this.paymentReference = paymentReference;
        this.discount = discount != null ? discount : BigDecimal.ZERO;
        this.items = items;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public BigDecimal getAmountTendered() {
        return amountTendered;
    }

    public void setAmountTendered(BigDecimal amountTendered) {
        this.amountTendered = amountTendered;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public void setDiscount(BigDecimal discount) {
        this.discount = discount != null ? discount : BigDecimal.ZERO;
    }

    public List<SaleItemRequest> getItems() {
        return items;
    }

    public void setItems(List<SaleItemRequest> items) {
        this.items = items;
    }
}
