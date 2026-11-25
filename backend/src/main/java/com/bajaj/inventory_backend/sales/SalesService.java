package com.bajaj.inventory_backend.sales;

import com.bajaj.inventory_backend.common.exception.InsufficientStockException;
import com.bajaj.inventory_backend.common.exception.ProductNotFoundException;
import com.bajaj.inventory_backend.entity.User;
import com.bajaj.inventory_backend.inventory.entity.StockMovementType;
import com.bajaj.inventory_backend.inventory.service.InventoryService;
import com.bajaj.inventory_backend.product.dto.ProductPriceDetails;
import com.bajaj.inventory_backend.product.service.DefaultProductCatalogService;
import com.bajaj.inventory_backend.product.service.ProductCatalogService;
import com.bajaj.inventory_backend.repository.UserRepository;
import com.bajaj.inventory_backend.sales.dto.ReceiptResponse;
import com.bajaj.inventory_backend.sales.dto.SaleCreateRequest;
import com.bajaj.inventory_backend.sales.dto.SaleItemRequest;
import com.bajaj.inventory_backend.sales.dto.SaleItemResponse;
import com.bajaj.inventory_backend.sales.dto.SaleResponse;
import com.bajaj.inventory_backend.sales.dto.SaleSummaryResponse;
import com.bajaj.inventory_backend.sales.exception.InvalidCheckoutException;
import com.bajaj.inventory_backend.sales.validation.CheckoutValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service layer for the Sales / Point of Sale (POS) module.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Orchestrate atomic checkout transactions with ACID guarantees.</li>
 *   <li>Enforce zero-trust price validation by loading authoritative prices from catalog.</li>
 *   <li>Calculate line totals, subtotal, discount bounds, and cash change due on the server.</li>
 *   <li>Freeze selling prices and inventory acquisition cost snapshots into line items.</li>
 *   <li>Delegate stock deduction to centralized {@link InventoryService} (Member 6).</li>
 *   <li>Guarantee transaction rollback on any failure (insufficient stock, invalid input).</li>
 *   <li>Generate unique sequential sale numbers and return finalized sale details.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class SalesService {

    private final SalesRepository salesRepository;
    private final CheckoutValidator checkoutValidator;
    private final InventoryService inventoryService;
    private final ProductCatalogService productCatalogService;
    private final UserRepository userRepository;
    private final ConcurrentMap<String, AtomicLong> monthlySequenceMap = new ConcurrentHashMap<>();

    @Autowired
    public SalesService(SalesRepository salesRepository,
                        @Autowired(required = false) CheckoutValidator checkoutValidator,
                        @Autowired(required = false) InventoryService inventoryService,
                        @Autowired(required = false) ProductCatalogService productCatalogService,
                        @Autowired(required = false) UserRepository userRepository) {
        this.salesRepository = salesRepository;
        this.checkoutValidator = checkoutValidator != null ? checkoutValidator : new CheckoutValidator();
        this.inventoryService = inventoryService;
        this.productCatalogService = productCatalogService != null ? productCatalogService : new DefaultProductCatalogService();
        this.userRepository = userRepository;
    }

    public SalesService(SalesRepository salesRepository,
                        CheckoutValidator checkoutValidator,
                        InventoryService inventoryService) {
        this(salesRepository, checkoutValidator, inventoryService, null, null);
    }

    public SalesService(SalesRepository salesRepository, CheckoutValidator checkoutValidator) {
        this(salesRepository, checkoutValidator, null, null, null);
    }

    public SalesService(SalesRepository salesRepository) {
        this(salesRepository, new CheckoutValidator(), null, null, null);
    }

    // ──────────────────────────────────────────────
    //  Checkout Transaction Orchestration
    // ──────────────────────────────────────────────

    /**
     * Executes the complete POS sales checkout transaction inside a single database transaction.
     *
     * <p>TRANSACTION GUARANTEES:
     * Participates in or starts an atomic transaction. If any failure occurs (e.g. stock deficit
     * during deduction, price mismatch, constraint failure), the transaction rolls back 100%,
     * ensuring no orphaned invoices or inconsistent stock deductions occur.
     *
     * @param request client checkout request
     * @param cashierUser cashier executing the checkout
     * @return completed sale details DTO
     * @throws InvalidCheckoutException if request validation fails
     * @throws InsufficientStockException if requested stock exceeds available on-hand stock
     * @throws ProductNotFoundException if any requested product is non-existent or inactive
     */
    @Transactional(
            propagation = Propagation.REQUIRED,
            isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class
    )
    public SaleResponse processCheckout(SaleCreateRequest request, User cashierUser) {
        if (request == null) {
            throw new InvalidCheckoutException("Checkout request cannot be null");
        }

        // 1. Validate items list integrity
        checkoutValidator.validateItems(request.getItems());

        // 2. Authoritative Product & Pricing Resolution (Zero-Trust Principle)
        List<ItemComputation> computations = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (SaleItemRequest itemReq : request.getItems()) {
            ProductPriceDetails product = productCatalogService.getProductDetails(itemReq.getProductId());
            if (product == null || !product.active()) {
                throw new ProductNotFoundException(itemReq.getProductId());
            }

            BigDecimal unitPrice = product.sellingPrice().setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(itemReq.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(lineTotal);

            computations.add(new ItemComputation(itemReq, product, unitPrice, lineTotal));
        }
        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);

        // 3. Authoritative checkout validation with server-calculated subtotal
        checkoutValidator.validateCheckout(request, subtotal);

        // 4. Calculate final totals & cash change due
        BigDecimal discount = request.getDiscount() != null
                ? request.getDiscount().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.subtract(discount).setScale(2, RoundingMode.HALF_UP);

        BigDecimal amountTendered = null;
        BigDecimal changeDue = null;
        if (request.getPaymentMethod() == PaymentMethod.CASH) {
            amountTendered = request.getAmountTendered().setScale(2, RoundingMode.HALF_UP);
            changeDue = amountTendered.subtract(total).setScale(2, RoundingMode.HALF_UP);
        }

        // 5. Pre-checkout stock availability check (Tier 1 fail-fast)
        if (this.inventoryService != null) {
            checkoutValidator.validateStockAvailability(request.getItems(), this.inventoryService);
        }

        // 6. Resolve cashier
        User cashier = cashierUser;
        if (cashier == null) {
            cashier = resolveCashierFromSecurityContext();
        }

        // 7. Mint next sequential saleNumber
        String saleNumber = generateNextSaleNumber();

        // 8. Create Sale record
        Sale sale = new Sale();
        sale.setSaleNumber(saleNumber);
        sale.setCustomerId(request.getCustomerId());
        sale.setSaleDate(Instant.now());
        sale.setStatus(SaleStatus.COMPLETED);
        sale.setSubtotal(subtotal);
        sale.setDiscount(discount);
        sale.setTotal(total);
        sale.setPaymentMethod(request.getPaymentMethod());
        sale.setCreatedBy(cashier);

        // 9. Save SaleItem line items with frozen selling price and cost snapshots
        for (ItemComputation comp : computations) {
            BigDecimal unitCost = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            if (this.inventoryService != null) {
                BigDecimal fetchedCost = this.inventoryService.getCurrentUnitCost(comp.itemReq.getProductId());
                if (fetchedCost != null) {
                    unitCost = fetchedCost.setScale(2, RoundingMode.HALF_UP);
                }
            }

            SaleItem saleItem = new SaleItem(
                    sale,
                    comp.itemReq.getProductId(),
                    comp.itemReq.getQuantity(),
                    comp.unitPrice,
                    unitCost,
                    comp.lineTotal
            );
            sale.addItem(saleItem);
        }

        Sale savedSale = salesRepository.save(sale);

        // 10. Centralized Stock Deduction via InventoryService (Strict Separation of Concerns)
        if (this.inventoryService != null) {
            // Sort by productId ascending to prevent database deadlocks across concurrent POS counters
            List<SaleItem> sortedItems = savedSale.getItems().stream()
                    .sorted(Comparator.comparing(SaleItem::getProductId))
                    .toList();

            for (SaleItem item : sortedItems) {
                this.inventoryService.deductStock(
                        item.getProductId(),
                        item.getQuantity(),
                        StockMovementType.SALE,
                        savedSale.getId()
                );
            }
        }

        // 11. Build and return completed SaleResponse
        String customerName = customerName(savedSale);

        List<SaleItemResponse> itemResponses = new ArrayList<>();
        for (SaleItem savedItem : savedSale.getItems()) {
            ItemComputation comp = findComputation(computations, savedItem.getProductId());
            itemResponses.add(new SaleItemResponse(
                    savedItem.getId(),
                    savedItem.getProductId(),
                    comp != null ? comp.product.partNumber() : "PART-" + savedItem.getProductId(),
                    comp != null ? comp.product.name() : "Part #" + savedItem.getProductId(),
                    savedItem.getQuantity(),
                    savedItem.getUnitSellingPrice(),
                    savedItem.getUnitCostSnapshot(),
                    savedItem.getLineTotal()
            ));
        }

        return new SaleResponse(
                savedSale.getId(),
                savedSale.getSaleNumber(),
                savedSale.getCustomerId(),
                customerName,
                savedSale.getSaleDate(),
                savedSale.getStatus().name(),
                savedSale.getSubtotal(),
                savedSale.getDiscount(),
                savedSale.getTotal(),
                savedSale.getPaymentMethod().name(),
                amountTendered,
                changeDue,
                request.getPaymentReference(),
                savedSale.getCreatedBy() != null ? savedSale.getCreatedBy().getId() : null,
                savedSale.getCreatedBy() != null ? savedSale.getCreatedBy().getFullName() : null,
                itemResponses
        );
    }

    @Transactional(
            propagation = Propagation.REQUIRED,
            isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class
    )
    public SaleResponse processCheckout(SaleCreateRequest request, Long cashierUserId) {
        User cashier = null;
        if (cashierUserId != null && this.userRepository != null) {
            cashier = userRepository.findById(cashierUserId).orElse(null);
        }
        return processCheckout(request, cashier);
    }

    @Transactional(
            propagation = Propagation.REQUIRED,
            isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class
    )
    public SaleResponse processCheckout(SaleCreateRequest request, Authentication authentication) {
        User cashier = null;
        if (authentication != null && authentication.getName() != null && this.userRepository != null) {
            cashier = userRepository.findByEmail(authentication.getName()).orElse(null);
        }
        return processCheckout(request, cashier);
    }

    @Transactional(
            propagation = Propagation.REQUIRED,
            isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class
    )
    public SaleResponse processCheckout(SaleCreateRequest request) {
        return processCheckout(request, (User) null);
    }

    // ──────────────────────────────────────────────
    //  Helper Methods
    // ──────────────────────────────────────────────

    public synchronized String generateNextSaleNumber() {
        String yearMonth = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String prefix = "INV-" + yearMonth + "-";

        AtomicLong counter = monthlySequenceMap.computeIfAbsent(prefix, p -> {
            long initialSeq = 1;
            if (salesRepository != null) {
                Optional<Sale> topSale = salesRepository.findTopBySaleNumberStartingWithOrderBySaleNumberDesc(p);
                if (topSale.isPresent()) {
                    String lastNumber = topSale.get().getSaleNumber();
                    try {
                        String seqStr = lastNumber.substring(p.length());
                        initialSeq = Long.parseLong(seqStr) + 1;
                    } catch (Exception ignored) {
                        initialSeq = salesRepository.count() + 1;
                    }
                }
            }
            return new AtomicLong(initialSeq);
        });

        String candidate;
        do {
            long seq = counter.getAndIncrement();
            candidate = String.format("%s%04d", prefix, seq);
        } while (salesRepository != null && salesRepository.existsBySaleNumber(candidate));

        return candidate;
    }

    private User resolveCashierFromSecurityContext() {
        if (this.userRepository == null) {
            return null;
        }
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null && !auth.getName().isBlank()) {
                return userRepository.findByEmail(auth.getName()).orElse(null);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private ItemComputation findComputation(List<ItemComputation> list, Long productId) {
        for (ItemComputation c : list) {
            if (c.itemReq.getProductId().equals(productId)) {
                return c;
            }
        }
        return null;
    }

    private record ItemComputation(
            SaleItemRequest itemReq,
            ProductPriceDetails product,
            BigDecimal unitPrice,
            BigDecimal lineTotal
    ) {}

    // ──────────────────────────────────────────────
    //  Checkout Validation
    // ──────────────────────────────────────────────

    public void validateCheckoutRequest(SaleCreateRequest request, BigDecimal authoritativeSubtotal) {
        checkoutValidator.validateCheckout(request, authoritativeSubtotal);
    }

    public void validateCheckoutRequest(SaleCreateRequest request, BigDecimal authoritativeSubtotal, InventoryService customInventoryService) {
        checkoutValidator.validateCheckout(request, authoritativeSubtotal, customInventoryService);
    }

    public void validateStockAvailability(List<SaleItemRequest> items) {
        if (this.inventoryService == null) {
            throw new IllegalStateException("Centralized InventoryService is not available in the current context");
        }
        checkoutValidator.validateStockAvailability(items, this.inventoryService);
    }

    public void validateStockAvailability(List<SaleItemRequest> items, InventoryService customInventoryService) {
        checkoutValidator.validateStockAvailability(items, customInventoryService);
    }

    // ──────────────────────────────────────────────
    //  Sale Retrieval & History
    // ──────────────────────────────────────────────

    /**
     * Retrieves full sale details mapped to {@link SaleResponse}.
     *
     * @param id sale primary key
     * @return populated sale response if found
     */
    public Optional<SaleResponse> getSaleDetails(Long id) {
        return salesRepository.findById(id).map(this::mapToSaleResponse);
    }

    /**
     * Retrieves structured receipt details mapped to {@link ReceiptResponse}.
     *
     * @param id sale primary key
     * @return formatted receipt response if found
     */
    public Optional<ReceiptResponse> getReceipt(Long id) {
        return salesRepository.findById(id).map(this::mapToReceiptResponse);
    }

    public SaleResponse mapToSaleResponse(Sale sale) {
        String customerName = customerName(sale);

        List<SaleItemResponse> itemResponses = new ArrayList<>();
        if (sale.getItems() != null) {
            for (SaleItem item : sale.getItems()) {
                String partNumber = "PART-" + item.getProductId();
                String productName = "Part #" + item.getProductId();
                if (this.productCatalogService != null) {
                    try {
                        ProductPriceDetails details = this.productCatalogService.getProductDetails(item.getProductId());
                        if (details != null) {
                            partNumber = details.partNumber();
                            productName = details.name();
                        }
                    } catch (Exception ignored) {
                    }
                }

                itemResponses.add(new SaleItemResponse(
                        item.getId(),
                        item.getProductId(),
                        partNumber,
                        productName,
                        item.getQuantity(),
                        item.getUnitSellingPrice(),
                        item.getUnitCostSnapshot(),
                        item.getLineTotal()
                ));
            }
        }

        BigDecimal amountTendered = null;
        BigDecimal changeDue = null;
        if (sale.getPaymentMethod() == PaymentMethod.CASH) {
            amountTendered = sale.getTotal();
            changeDue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return new SaleResponse(
                sale.getId(),
                sale.getSaleNumber(),
                sale.getCustomerId(),
                customerName,
                sale.getSaleDate(),
                sale.getStatus() != null ? sale.getStatus().name() : "COMPLETED",
                sale.getSubtotal(),
                sale.getDiscount(),
                sale.getTotal(),
                sale.getPaymentMethod() != null ? sale.getPaymentMethod().name() : "CASH",
                amountTendered,
                changeDue,
                null,
                sale.getCreatedBy() != null ? sale.getCreatedBy().getId() : null,
                sale.getCreatedBy() != null ? sale.getCreatedBy().getFullName() : null,
                itemResponses
        );
    }

    public ReceiptResponse mapToReceiptResponse(Sale sale) {
        String customerName = customerName(sale);

        List<ReceiptResponse.ReceiptItem> receiptItems = new ArrayList<>();
        if (sale.getItems() != null) {
            for (SaleItem item : sale.getItems()) {
                String partNumber = "PART-" + item.getProductId();
                String description = "Part #" + item.getProductId();
                if (this.productCatalogService != null) {
                    try {
                        ProductPriceDetails details = this.productCatalogService.getProductDetails(item.getProductId());
                        if (details != null) {
                            partNumber = details.partNumber();
                            description = details.name();
                        }
                    } catch (Exception ignored) {
                    }
                }

                receiptItems.add(new ReceiptResponse.ReceiptItem(
                        partNumber,
                        description,
                        item.getQuantity(),
                        item.getUnitSellingPrice(),
                        item.getLineTotal()
                ));
            }
        }

        ReceiptResponse.StoreInfo storeInfo = new ReceiptResponse.StoreInfo(
                "Bajaj Genuine Spare Parts - Central Depot",
                "45 Commercial Road, Automotive Hub",
                "+91 80 2345 6789",
                "29AAAAA0000A1Z5"
        );

        ReceiptResponse.ReceiptInfo receiptInfo = new ReceiptResponse.ReceiptInfo(
                sale.getSaleNumber(),
                sale.getSaleDate(),
                sale.getCreatedBy() != null ? sale.getCreatedBy().getFullName() : "Cashier",
                customerName,
                null
        );

        BigDecimal taxIncluded = sale.getTotal()
                .multiply(new BigDecimal("18"))
                .divide(new BigDecimal("118"), 2, RoundingMode.HALF_UP);

        ReceiptResponse.ReceiptTotals totals = new ReceiptResponse.ReceiptTotals(
                sale.getSubtotal(),
                sale.getDiscount(),
                taxIncluded,
                sale.getTotal()
        );

        ReceiptResponse.ReceiptPayment payment = new ReceiptResponse.ReceiptPayment(
                sale.getPaymentMethod() != null ? sale.getPaymentMethod().name() : "CASH",
                sale.getTotal(),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                null
        );

        String footerNote = "Thank you for buying Bajaj Genuine Parts! Goods once sold can be exchanged within 7 days in original condition.";

        return new ReceiptResponse(
                storeInfo,
                receiptInfo,
                receiptItems,
                totals,
                payment,
                footerNote
        );
    }

    public Optional<Sale> findById(Long id) {
        return salesRepository.findById(id);
    }

    public Optional<Sale> findBySaleNumber(String saleNumber) {
        return salesRepository.findBySaleNumber(saleNumber);
    }

    public Page<Sale> findAll(Pageable pageable) {
        return salesRepository.findAll(pageable);
    }

    /**
     * Queries past sales with optional filtering by status, payment method, and invoice search query,
     * returning a page of {@link SaleSummaryResponse}.
     *
     * @param status optional status filter (e.g. COMPLETED)
     * @param paymentMethod optional payment method filter (e.g. CASH)
     * @param search optional sale number search term
     * @param pageable pagination parameters
     * @return page of summary records
     */
    public Page<SaleSummaryResponse> findSalesSummary(
            String status,
            PaymentMethod paymentMethod,
            String search,
            Pageable pageable) {

        org.springframework.data.jpa.domain.Specification<Sale> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            if (status != null && !status.isBlank()) {
                try {
                    SaleStatus saleStatus = SaleStatus.valueOf(status.trim().toUpperCase());
                    predicates.add(cb.equal(root.get("status"), saleStatus));
                } catch (IllegalArgumentException ignored) {
                }
            }

            if (paymentMethod != null) {
                predicates.add(cb.equal(root.get("paymentMethod"), paymentMethod));
            }

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("saleNumber")), pattern));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Page<Sale> salesPage = salesRepository.findAll(spec, pageable);
        return salesPage.map(this::mapToSaleSummaryResponse);
    }

    /**
     * Overloaded helper allowing string-based payment method query parameters.
     */
    public Page<SaleSummaryResponse> findSalesSummary(
            String status,
            String paymentMethod,
            String search,
            Pageable pageable) {
        PaymentMethod pm = null;
        if (paymentMethod != null && !paymentMethod.isBlank()) {
            try {
                pm = PaymentMethod.valueOf(paymentMethod.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return findSalesSummary(status, pm, search, pageable);
    }

    /**
     * Overloaded helper accepting enum status.
     */
    public Page<SaleSummaryResponse> findSalesSummary(
            SaleStatus status,
            PaymentMethod paymentMethod,
            String search,
            Pageable pageable) {
        return findSalesSummary(status != null ? status.name() : null, paymentMethod, search, pageable);
    }

    /**
     * Queries all past sales summaries with default pagination.
     */
    public Page<SaleSummaryResponse> findSalesSummary(Pageable pageable) {
        return findSalesSummary((String) null, (PaymentMethod) null, null, pageable);
    }

    public SaleSummaryResponse mapToSaleSummaryResponse(Sale sale) {
        String customerName = customerName(sale);

        int itemCount = 0;
        if (sale != null && sale.getItems() != null) {
            for (SaleItem item : sale.getItems()) {
                itemCount += (item.getQuantity() != null ? item.getQuantity() : 1);
            }
        }

        return new SaleSummaryResponse(
                sale != null ? sale.getId() : null,
                sale != null ? sale.getSaleNumber() : null,
                sale != null ? sale.getCustomerId() : null,
                customerName,
                sale != null ? sale.getSaleDate() : null,
                sale != null && sale.getStatus() != null ? sale.getStatus().name() : "COMPLETED",
                itemCount,
                sale != null ? sale.getSubtotal() : null,
                sale != null ? sale.getDiscount() : null,
                sale != null ? sale.getTotal() : null,
                sale != null && sale.getPaymentMethod() != null ? sale.getPaymentMethod().name() : "CASH",
                sale != null && sale.getCreatedBy() != null ? sale.getCreatedBy().getId() : null,
                sale != null && sale.getCreatedBy() != null ? sale.getCreatedBy().getFullName() : null
        );
    }

    /**
     * Resolves display customer name from customer ID.
     * Defaults to "Walk-in Customer" for anonymous checkouts.
     *
     * @param customerId optional customer database ID
     * @return display customer name
     */
    public String customerName(Long customerId) {
        return customerId == null ? "Walk-in Customer" : "Customer #" + customerId;
    }

    /**
     * Resolves display customer name from a Sale entity.
     *
     * @param sale the sale entity
     * @return display customer name
     */
    public String customerName(Sale sale) {
        return customerName(sale != null ? sale.getCustomerId() : null);
    }

    public boolean existsBySaleNumber(String saleNumber) {
        return salesRepository.existsBySaleNumber(saleNumber);
    }
}
