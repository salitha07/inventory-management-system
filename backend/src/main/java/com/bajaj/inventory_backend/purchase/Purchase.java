
package com.bajaj.inventory_backend.purchase;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Purchase {

    private Long id;

    private String purchaseNumber;

    private Long supplierId;

    private String supplierInvoiceNumber;

    private LocalDate purchaseDate;

    private String status;

    private BigDecimal subtotal;

    private BigDecimal discount;

    private BigDecimal total;

    private Long createdBy;
}
