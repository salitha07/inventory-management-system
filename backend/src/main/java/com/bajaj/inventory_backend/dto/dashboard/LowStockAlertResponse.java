
package com.bajaj.inventory_backend.dto.dashboard;

public class LowStockAlertResponse {

    private Long productId;
    private String productName;
    private String sku;
    private int currentQuantity;
    private int reorderLevel;

    public LowStockAlertResponse(
            Long productId,
            String productName,
            String sku,
            int currentQuantity,
            int reorderLevel) {

        this.productId = productId;
        this.productName = productName;
        this.sku = sku;
        this.currentQuantity = currentQuantity;
        this.reorderLevel = reorderLevel;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public String getSku() {
        return sku;
    }

    public int getCurrentQuantity() {
        return currentQuantity;
    }

    public int getReorderLevel() {
        return reorderLevel;
    }
}
