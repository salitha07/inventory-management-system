package com.bajaj.inventory_backend.purchase;

import com.bajaj.inventory_backend.repository.PurchaseRepository;
import com.bajaj.inventory_backend.purchase.dto.CreatePurchaseRequest;
import com.bajaj.inventory_backend.purchase.dto.PurchaseItemRequest;
import com.bajaj.inventory_backend.purchase.dto.PurchaseItemResponse;
import com.bajaj.inventory_backend.purchase.dto.PurchaseResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.bajaj.inventory_backend.entity.Supplier;
import com.bajaj.inventory_backend.product.Product;
import com.bajaj.inventory_backend.repository.SupplierRepository;
import com.bajaj.inventory_backend.repository.ProductRepository;

@Service
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;

    public PurchaseService(
            PurchaseRepository purchaseRepository,
            SupplierRepository supplierRepository,
            ProductRepository productRepository
    ) {
        this.purchaseRepository = purchaseRepository;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<PurchaseResponse> getAllPurchases() {
        return purchaseRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }
    @Transactional(readOnly = true)
    public List<PurchaseResponse> searchPurchases(
            String purchaseNumber,
            Long supplierId,
            PurchaseStatus status,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        if (fromDate != null && toDate != null
                && fromDate.isAfter(toDate)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "From date cannot be after to date"
            );
        }

        return purchaseRepository.searchPurchases(
                        purchaseNumber,
                        supplierId,
                        status,
                        fromDate,
                        toDate
                )
                .stream()
                .map(this::mapToResponse)
                .toList();
    }
    private void validateSupplier(Long supplierId) {

        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Supplier not found: " + supplierId
                ));

        if (!supplier.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Supplier is inactive: " + supplierId
            );
        }
    }
    private void validateProduct(Long productId) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found: " + productId
                ));

        if (!product.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Product is inactive: " + productId
            );
        }
    }
    @Transactional(readOnly = true)
    public PurchaseResponse getPurchaseById(Long id) {

        Purchase purchase = purchaseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Purchase not found: " + id
                ));

        return mapToResponse(purchase);
    }

    @Transactional
    public PurchaseResponse createPurchase(
            CreatePurchaseRequest request,
            Long createdBy
    ) {
        validateSupplier(request.supplierId());
        Purchase purchase = new Purchase();

        purchase.setPurchaseNumber(
                "PUR-" + UUID.randomUUID()
                        .toString()
                        .substring(0, 8)
                        .toUpperCase()
        );

        purchase.setSupplierId(request.supplierId());
        purchase.setSupplierInvoiceNumber(
                request.supplierInvoiceNumber()
        );
        purchase.setPurchaseDate(request.purchaseDate());
        purchase.setStatus(PurchaseStatus.DRAFT);
        purchase.setCreatedBy(createdBy);

        BigDecimal subtotal = BigDecimal.ZERO;

        List<PurchaseItem> items = new ArrayList<>();

        for (PurchaseItemRequest itemRequest : request.items()) {

            validateProduct(itemRequest.productId());

            BigDecimal lineTotal = itemRequest.unitCost()
                    .multiply(BigDecimal.valueOf(
                            itemRequest.quantity()
                    ));

            PurchaseItem item = new PurchaseItem();

            item.setPurchase(purchase);
            item.setProductId(itemRequest.productId());
            item.setQuantity(itemRequest.quantity());
            item.setUnitCost(itemRequest.unitCost());
            item.setLineTotal(lineTotal);

            items.add(item);

            subtotal = subtotal.add(lineTotal);
        }

        BigDecimal discount = request.discount();

        if (discount.compareTo(subtotal) > 0) {
            throw new IllegalArgumentException(
                    "Discount cannot exceed subtotal"
            );
        }

        purchase.setSubtotal(subtotal);
        purchase.setDiscount(discount);
        purchase.setTotal(subtotal.subtract(discount));
        purchase.setItems(items);

        Purchase savedPurchase =
                purchaseRepository.save(purchase);

        return mapToResponse(savedPurchase);
    }
    @Transactional
    public PurchaseResponse cancelPurchase(Long id) {

        Purchase purchase = purchaseRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Purchase not found: " + id
                ));

        if (purchase.getStatus() != PurchaseStatus.DRAFT) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only draft purchases can be cancelled"
            );
        }

        purchase.setStatus(PurchaseStatus.CANCELLED);

        Purchase cancelledPurchase =
                purchaseRepository.save(purchase);

        return mapToResponse(cancelledPurchase);
    }
    private PurchaseResponse mapToResponse(Purchase purchase) {

        List<PurchaseItemResponse> itemResponses =
                purchase.getItems()
                        .stream()
                        .map(item -> new PurchaseItemResponse(
                                item.getId(),
                                item.getProductId(),
                                item.getQuantity(),
                                item.getUnitCost(),
                                item.getLineTotal()
                        ))
                        .toList();

        return new PurchaseResponse(
                purchase.getId(),
                purchase.getPurchaseNumber(),
                purchase.getSupplierId(),
                purchase.getSupplierInvoiceNumber(),
                purchase.getPurchaseDate(),
                purchase.getStatus().name(),
                purchase.getSubtotal(),
                purchase.getDiscount(),
                purchase.getTotal(),
                purchase.getCreatedBy(),
                itemResponses
        );
    }
}