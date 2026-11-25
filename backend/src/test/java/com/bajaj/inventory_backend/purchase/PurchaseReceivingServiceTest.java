package com.bajaj.inventory_backend.purchase;

import com.bajaj.inventory_backend.entity.User;
import com.bajaj.inventory_backend.entity.Role;
import com.bajaj.inventory_backend.entity.RoleName;
import com.bajaj.inventory_backend.inventory.core.InventoryOperations;
import com.bajaj.inventory_backend.inventory.dto.InventoryContracts.StockChangeCommand;
import com.bajaj.inventory_backend.repository.PurchaseRepository;
import com.bajaj.inventory_backend.repository.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseReceivingServiceTest {

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PurchaseService purchaseService;

    @Mock
    private ObjectProvider<InventoryOperations> inventoryProvider;

    @Mock
    private InventoryOperations inventoryOperations;

    private PurchaseReceivingService receivingService;

    @BeforeEach
    void setUp() {

        SecurityContextHolder.clearContext();

        receivingService = new PurchaseReceivingService(
                purchaseRepository,
                userRepository,
                purchaseService,
                inventoryProvider
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(
            RoleName roleName,
            boolean active
    ) {

        UserDetails principal =
                org.springframework.security.core.userdetails.User
                        .withUsername("receiver@test.com")
                        .password("unused")
                        .roles(roleName.name())
                        .build();

        var authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        User user = mock(User.class);

        when(user.isActive()).thenReturn(active);

        if (active) {

            Role role = mock(Role.class);

            when(user.getId()).thenReturn(10L);
            when(user.getRole()).thenReturn(role);
            when(role.getName()).thenReturn(roleName);
        }

        when(userRepository.findByEmail("receiver@test.com"))
                .thenReturn(Optional.of(user));
    }

    private Purchase createPurchase(
            PurchaseStatus status
    ) {

        Purchase purchase = new Purchase();

        purchase.setStatus(status);

        PurchaseItem item = new PurchaseItem();

        item.setPurchase(purchase);
        item.setProductId(100L);
        item.setQuantity(5);

        purchase.setItems(
                new ArrayList<>(List.of(item))
        );

        return purchase;
    }

    private Purchase mockDraftPurchase() {

        Purchase purchase = spy(
                createPurchase(PurchaseStatus.DRAFT)
        );

        when(purchase.getId()).thenReturn(1L);

        when(purchaseRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(purchase));

        return purchase;
    }

    @Test
    void shouldRejectUnauthenticatedReceiver() {

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> receivingService.receivePurchase(1L)
                );

        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exception.getStatusCode()
        );

        verifyNoInteractions(inventoryOperations);
    }

    @Test
    void shouldRejectInactiveReceiver() {

        authenticate(RoleName.ADMIN, false);

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> receivingService.receivePurchase(1L)
                );

        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exception.getStatusCode()
        );

        verifyNoInteractions(inventoryOperations);
    }

    @Test
    void shouldRejectUnauthorizedRole() {

        authenticate(RoleName.CASHIER, true);

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> receivingService.receivePurchase(1L)
                );

        assertEquals(
                HttpStatus.FORBIDDEN,
                exception.getStatusCode()
        );

        verifyNoInteractions(inventoryOperations);
    }

    @Test
    void shouldRejectInvalidPurchaseId() {

        authenticate(RoleName.ADMIN, true);

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> receivingService.receivePurchase(0L)
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        verifyNoInteractions(inventoryOperations);
    }

    @Test
    void shouldRejectMissingPurchase() {

        authenticate(RoleName.ADMIN, true);

        when(purchaseRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> receivingService.receivePurchase(1L)
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );
    }

    @Test
    void shouldRejectAlreadyReceivedPurchase() {

        authenticate(RoleName.ADMIN, true);

        Purchase purchase =
                createPurchase(PurchaseStatus.RECEIVED);

        when(purchaseRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(purchase));

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> receivingService.receivePurchase(1L)
                );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verifyNoInteractions(inventoryOperations);
    }

    @Test
    void shouldRejectCancelledPurchase() {

        authenticate(RoleName.MANAGER, true);

        Purchase purchase =
                createPurchase(PurchaseStatus.CANCELLED);

        when(purchaseRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(purchase));

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> receivingService.receivePurchase(1L)
                );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verifyNoInteractions(inventoryOperations);
    }

    @Test
    void shouldRejectPurchaseWithoutItems() {

        authenticate(RoleName.ADMIN, true);

        Purchase purchase =
                createPurchase(PurchaseStatus.DRAFT);

        purchase.setItems(new ArrayList<>());

        when(purchaseRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(purchase));

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> receivingService.receivePurchase(1L)
                );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verifyNoInteractions(inventoryOperations);
    }

    @Test
    void shouldRejectUnavailableInventoryIntegration() {

        authenticate(RoleName.ADMIN, true);

        Purchase purchase =
                createPurchase(PurchaseStatus.DRAFT);

        when(purchaseRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(purchase));

        when(inventoryProvider.getIfAvailable())
                .thenReturn(null);

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> receivingService.receivePurchase(1L)
                );

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                exception.getStatusCode()
        );

        assertEquals(
                PurchaseStatus.DRAFT,
                purchase.getStatus()
        );

        verify(purchaseRepository, never())
                .save(any(Purchase.class));
    }

    @Test
    void shouldReceiveDraftPurchaseSuccessfully() {

        authenticate(RoleName.ADMIN, true);

        Purchase purchase = mockDraftPurchase();

        when(inventoryProvider.getIfAvailable())
                .thenReturn(inventoryOperations);

        receivingService.receivePurchase(1L);

        assertEquals(
                PurchaseStatus.RECEIVED,
                purchase.getStatus()
        );

        verify(inventoryOperations)
                .receiveStock(argThat(command ->
                        command.productId().equals(100L)
                                && command.quantity().equals(5)
                                && command.context()
                                .referenceId().equals(1L)
                                && command.context()
                                .referenceType().equals("PURCHASE")
                                && command.context()
                                .actor().userId().equals(10L)
                ));

        verify(purchaseRepository).save(purchase);

        verify(purchaseService).getPurchaseById(1L);
    }

    @Test
    void shouldKeepDraftWhenInventoryOperationFails() {

        authenticate(RoleName.ADMIN, true);

        Purchase purchase = mockDraftPurchase();

        when(inventoryProvider.getIfAvailable())
                .thenReturn(inventoryOperations);

        when(inventoryOperations.receiveStock(
                any(StockChangeCommand.class)
        )).thenThrow(
                new IllegalStateException("Stock update failed")
        );

        assertThrows(
                IllegalStateException.class,
                () -> receivingService.receivePurchase(1L)
        );

        assertEquals(
                PurchaseStatus.DRAFT,
                purchase.getStatus()
        );

        verify(purchaseRepository, never())
                .save(any(Purchase.class));
    }
}