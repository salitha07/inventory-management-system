package com.bajaj.inventory_backend.purchase;

import com.bajaj.inventory_backend.entity.RoleName;
import com.bajaj.inventory_backend.inventory.core.InventoryOperations;
import com.bajaj.inventory_backend.inventory.dto.InventoryContracts.*;
import com.bajaj.inventory_backend.inventory.persistence.StockPersistenceException;
import com.bajaj.inventory_backend.repository.PurchaseRepository;
import com.bajaj.inventory_backend.repository.UserRepository;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;

@Service
public class PurchaseReceivingService {

    private final PurchaseRepository purchaseRepository;
    private final UserRepository userRepository;
    private final PurchaseService purchaseService;
    private final ObjectProvider<InventoryOperations> inventoryProvider;

    public PurchaseReceivingService(
            PurchaseRepository purchaseRepository,
            UserRepository userRepository,
            PurchaseService purchaseService,
            ObjectProvider<InventoryOperations> inventoryProvider
    ) {
        this.purchaseRepository = purchaseRepository;
        this.userRepository = userRepository;
        this.purchaseService = purchaseService;
        this.inventoryProvider = inventoryProvider;
    }

    @Transactional
    public com.bajaj.inventory_backend.purchase.dto.PurchaseResponse receivePurchase(Long id) {

        AuthenticatedActor actor = requireReceivingActor();

        if (id == null || id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid purchase ID"
            );
        }

        Purchase purchase = purchaseRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Purchase not found: " + id
                ));

        if (purchase.getStatus() != PurchaseStatus.DRAFT) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only draft purchases can be received"
            );
        }

        if (purchase.getItems().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Purchase contains no items"
            );
        }

        InventoryOperations inventory = inventoryProvider.getIfAvailable();

        if (inventory == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Inventory receiving integration is unavailable"
            );
        }

        MovementContext context = new MovementContext(
                "PURCHASE",
                purchase.getId(),
                "Purchase receipt",
                actor
        );

        var items = purchase.getItems()
                .stream()
                .sorted(Comparator.comparing(PurchaseItem::getProductId))
                .toList();

        for (PurchaseItem item : items) {

            StockChangeCommand command = new StockChangeCommand(
                    item.getProductId(),
                    item.getQuantity(),
                    context
            );

            try {
                inventory.receiveStock(command);

            } catch (StockPersistenceException exception) {

                if (exception.reason()
                        == StockPersistenceException.Reason.STOCK_NOT_INITIALIZED) {

                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Stock is not initialized for product: "
                                    + item.getProductId(),
                            exception
                    );
                }

                throw exception;
            }
        }

        purchase.setStatus(PurchaseStatus.RECEIVED);
        purchaseRepository.save(purchase);

        return purchaseService.getPurchaseById(id);
    }

    private AuthenticatedActor requireReceivingActor() {

        var authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof UserDetails principal)
                || !principal.isEnabled()) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        var user = userRepository.findByEmail(principal.getUsername())
                .filter(account -> account.isActive()
                        && account.getId() != null
                        && account.getId() > 0)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user is unavailable"
                ));

        RoleName role = user.getRole() == null
                ? null
                : user.getRole().getName();

        if ((role != RoleName.ADMIN && role != RoleName.MANAGER)
                || authentication.getAuthorities()
                .stream()
                .noneMatch(authority ->
                        ("ROLE_" + role.name())
                                .equals(authority.getAuthority()))) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Purchase receiving requires an authorized role"
            );
        }

        return new AuthenticatedActor(user.getId());
    }
}