package com.bajaj.inventory_backend.purchase;

import com.bajaj.inventory_backend.entity.Supplier;
import com.bajaj.inventory_backend.product.Product;
import com.bajaj.inventory_backend.repository.ProductRepository;
import com.bajaj.inventory_backend.repository.PurchaseRepository;
import com.bajaj.inventory_backend.repository.SupplierRepository;

import com.bajaj.inventory_backend.purchase.dto.CreatePurchaseRequest;
import com.bajaj.inventory_backend.purchase.dto.PurchaseItemRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class PurchaseServiceTest {

    private PurchaseRepository purchaseRepository;
    private SupplierRepository supplierRepository;
    private ProductRepository productRepository;

    private PurchaseService purchaseService;

    @BeforeEach
    void setUp() {

        purchaseRepository = mock(PurchaseRepository.class);
        supplierRepository = mock(SupplierRepository.class);
        productRepository = mock(ProductRepository.class);

        purchaseService = new PurchaseService(
                purchaseRepository,
                supplierRepository,
                productRepository
        );
    }

    private void prepareValidReferences() {

        Supplier supplier = mock(Supplier.class);
        Product product = mock(Product.class);

        when(supplier.isActive()).thenReturn(true);
        when(product.isActive()).thenReturn(true);

        when(supplierRepository.findById(1L))
                .thenReturn(Optional.of(supplier));

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));
    }

    private CreatePurchaseRequest request(
            int quantity,
            String unitCost,
            String discount
    ) {

        return new CreatePurchaseRequest(
                1L,
                "INV-001",
                LocalDate.now(),
                new BigDecimal(discount),
                List.of(
                        new PurchaseItemRequest(
                                1L,
                                quantity,
                                new BigDecimal(unitCost)
                        )
                )
        );
    }

    @Test
    void shouldCreatePurchaseWithCorrectAmounts() {

        prepareValidReferences();

        when(purchaseRepository.save(any(Purchase.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = purchaseService.createPurchase(
                request(5, "100.00", "50.00"),
                1L
        );

        assertEquals(
                new BigDecimal("500.00"),
                response.subtotal()
        );

        assertEquals(
                new BigDecimal("450.00"),
                response.total()
        );

        assertEquals("DRAFT", response.status());

        verify(purchaseRepository).save(any(Purchase.class));
    }

    @Test
    void shouldRejectMissingSupplier() {

        when(supplierRepository.findById(1L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> purchaseService.createPurchase(
                        request(5, "100.00", "0.00"),
                        1L
                )
        );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        verify(purchaseRepository, never())
                .save(any(Purchase.class));
    }

    @Test
    void shouldRejectInactiveSupplier() {

        Supplier supplier = mock(Supplier.class);

        when(supplier.isActive()).thenReturn(false);

        when(supplierRepository.findById(1L))
                .thenReturn(Optional.of(supplier));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> purchaseService.createPurchase(
                        request(5, "100.00", "0.00"),
                        1L
                )
        );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        verify(purchaseRepository, never())
                .save(any(Purchase.class));
    }

    @Test
    void shouldRejectMissingProduct() {

        prepareValidReferences();

        when(productRepository.findById(1L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> purchaseService.createPurchase(
                        request(5, "100.00", "0.00"),
                        1L
                )
        );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        verify(purchaseRepository, never())
                .save(any(Purchase.class));
    }

    @Test
    void shouldRejectInactiveProduct() {

        prepareValidReferences();

        Product product = mock(Product.class);

        when(product.isActive()).thenReturn(false);

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> purchaseService.createPurchase(
                        request(5, "100.00", "0.00"),
                        1L
                )
        );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        verify(purchaseRepository, never())
                .save(any(Purchase.class));
    }

    @Test
    void shouldRejectDiscountGreaterThanSubtotal() {

        prepareValidReferences();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> purchaseService.createPurchase(
                        request(2, "100.00", "250.00"),
                        1L
                )
        );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        verify(purchaseRepository, never())
                .save(any(Purchase.class));
    }

    @Test
    void shouldRejectAmountOverflow() {

        prepareValidReferences();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> purchaseService.createPurchase(
                        request(2, "9999999999.99", "0.00"),
                        1L
                )
        );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        verify(purchaseRepository, never())
                .save(any(Purchase.class));
    }

    @Test
    void shouldRejectInvalidMonetaryPrecision() {

        prepareValidReferences();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> purchaseService.createPurchase(
                        request(1, "100.123", "0.00"),
                        1L
                )
        );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );
    }

    @Test
    void shouldReturnNotFoundForMissingPurchase() {

        when(purchaseRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> purchaseService.getPurchaseById(99L)
        );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );
    }

    @Test
    void shouldRejectInvalidPurchaseId() {

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> purchaseService.getPurchaseById(0L)
        );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );
    }

    @Test
    void shouldCancelDraftPurchase() {

        Purchase purchase = new Purchase();
        purchase.setStatus(PurchaseStatus.DRAFT);
        purchase.setItems(List.of());

        when(purchaseRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(purchase));

        when(purchaseRepository.save(any(Purchase.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        purchaseService.cancelPurchase(1L);

        assertEquals(
                PurchaseStatus.CANCELLED,
                purchase.getStatus()
        );

        verify(purchaseRepository).save(purchase);
    }

    @Test
    void shouldRejectCancellationOfReceivedPurchase() {

        Purchase purchase = new Purchase();
        purchase.setStatus(PurchaseStatus.RECEIVED);

        when(purchaseRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(purchase));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> purchaseService.cancelPurchase(1L)
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verify(purchaseRepository, never())
                .save(any(Purchase.class));
    }

    @Test
    void shouldRejectInvalidSearchDateRange() {

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> purchaseService.searchPurchases(
                        null,
                        null,
                        null,
                        LocalDate.of(2026, 12, 31),
                        LocalDate.of(2026, 1, 1)
                )
        );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );
    }
}