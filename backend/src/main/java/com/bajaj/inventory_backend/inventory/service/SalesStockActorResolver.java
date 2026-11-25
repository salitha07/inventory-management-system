package com.bajaj.inventory_backend.inventory.service;

import com.bajaj.inventory_backend.entity.RoleName;
import com.bajaj.inventory_backend.inventory.dto.InventoryContracts.AuthenticatedActor;
import com.bajaj.inventory_backend.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/** Actor resolution for the documented Sales checkout permission only. */
@Component
class SalesStockActorResolver {
    private final UserRepository users;

    SalesStockActorResolver(UserRepository users) {
        this.users = users;
    }

    AuthenticatedActor requireCheckoutActor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof UserDetails principal)
                || !principal.isEnabled() || principal.getUsername() == null || principal.getUsername().isBlank()) {
            throw new AuthenticationCredentialsNotFoundException("An authenticated checkout user is required");
        }
        var user = users.findByEmail(principal.getUsername())
                .filter(account -> account.isActive() && account.getId() != null && account.getId() > 0)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Checkout user is unavailable"));
        RoleName role = user.getRole() == null ? null : user.getRole().getName();
        if ((role != RoleName.CASHIER && role != RoleName.MANAGER && role != RoleName.ADMIN)
                || authentication.getAuthorities().stream().noneMatch(a -> ("ROLE_" + role.name()).equals(a.getAuthority()))) {
            throw new AccessDeniedException("Checkout stock deduction requires an authorized Sales role");
        }
        return new AuthenticatedActor(user.getId());
    }
}
