package com.bajaj.inventory_backend.inventory.service;

import com.bajaj.inventory_backend.entity.Role;
import com.bajaj.inventory_backend.entity.RoleName;
import com.bajaj.inventory_backend.entity.User;
import com.bajaj.inventory_backend.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SalesStockActorResolverTest {
    private static final String EMAIL = "cashier@example.test";
    private final UserRepository users = mock(UserRepository.class);
    private final SalesStockActorResolver resolver = new SalesStockActorResolver(users);

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesEachDocumentedCheckoutRoleToThePersistedUserId() {
        for (var role : List.of(RoleName.CASHIER, RoleName.MANAGER, RoleName.ADMIN)) {
            authenticate(role, true);
            account(role, true);
            assertEquals(42L, resolver.requireCheckoutActor().userId());
        }
    }

    @Test
    void rejectsMissingAnonymousAndUnauthenticatedContextsBeforeUserLookup() {
        assertThrows(AuthenticationCredentialsNotFoundException.class, resolver::requireCheckoutActor);
        var context = SecurityContextHolder.getContext();
        context.setAuthentication(new AnonymousAuthenticationToken("test", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        assertThrows(AuthenticationCredentialsNotFoundException.class, resolver::requireCheckoutActor);
        context.setAuthentication(UsernamePasswordAuthenticationToken.unauthenticated(EMAIL, null));
        assertThrows(AuthenticationCredentialsNotFoundException.class, resolver::requireCheckoutActor);
        verifyNoInteractions(users);
    }

    @Test
    void rejectsDisabledPrincipalAndMissingOrInactiveAccount() {
        authenticate(RoleName.CASHIER, false);
        assertThrows(AuthenticationCredentialsNotFoundException.class, resolver::requireCheckoutActor);
        verifyNoInteractions(users);
        authenticate(RoleName.CASHIER, true);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());
        assertThrows(AuthenticationCredentialsNotFoundException.class, resolver::requireCheckoutActor);
        account(RoleName.CASHIER, false);
        assertThrows(AuthenticationCredentialsNotFoundException.class, resolver::requireCheckoutActor);
    }

    @Test
    void deniesInventoryStaffAndStaleRoleAuthorities() {
        authenticate(RoleName.INVENTORY_STAFF, true);
        account(RoleName.INVENTORY_STAFF, true);
        assertThrows(AccessDeniedException.class, resolver::requireCheckoutActor);
        authenticate(RoleName.CASHIER, true);
        account(RoleName.MANAGER, true);
        assertThrows(AccessDeniedException.class, resolver::requireCheckoutActor);
    }

    private void authenticate(RoleName role, boolean enabled) {
        var principal = org.springframework.security.core.userdetails.User.withUsername(EMAIL)
                .password("fixture").roles(role.name()).disabled(!enabled).build();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    private void account(RoleName name, boolean active) {
        var role = new Role();
        role.setName(name);
        var user = mock(User.class);
        when(user.getId()).thenReturn(42L);
        when(user.isActive()).thenReturn(active);
        when(user.getRole()).thenReturn(role);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }
}
