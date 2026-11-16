
package com.bajaj.inventory_backend.purchase;

import java.math.BigDecimal;

public class PurchaseItem {

    private Long id;

    private Long purchaseId;

    private Long productId;

    private Integer quantity;

    private BigDecimal unitCost;

    private BigDecimal lineTotal;
}
