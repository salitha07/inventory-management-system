package com.bajaj.inventory_backend.purchase;

import com.bajaj.inventory_backend.config.SecurityConfig;
import com.bajaj.inventory_backend.entity.User;
import com.bajaj.inventory_backend.purchase.dto.CreatePurchaseRequest;
import com.bajaj.inventory_backend.purchase.dto.PurchaseResponse;
import com.bajaj.inventory_backend.repository.UserRepository;
import com.bajaj.inventory_backend.security.CustomUserDetailsService;
import com.bajaj.inventory_backend.security.JwtAuthenticationFilter;
import com.bajaj.inventory_backend.service.JwtService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PurchaseController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class
})
class PurchaseControllerTest {

    private static final String BASE_URL =
            "/api/v1/purchases";

    private static final String EMAIL =
            "purchase@example.test";

    private static final String VALID_REQUEST = """
            {
                "supplierId": 1,
                "supplierInvoiceNumber": "INV-001",
                "purchaseDate": "2026-11-25",
                "discount": 50.00,
                "items": [
                    {
                        "productId": 1,
                        "quantity": 5,
                        "unitCost": 100.00
                    }
                ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PurchaseService purchaseService;

    @MockitoBean
    private PurchaseReceivingService receivingService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    private PurchaseResponse response(String status) {

        return new PurchaseResponse(
                1L,
                "PUR-TEST001",
                1L,
                "INV-001",
                LocalDate.of(2026, 11, 25),
                status,
                new BigDecimal("500.00"),
                new BigDecimal("50.00"),
                new BigDecimal("450.00"),
                10L,
                List.of()
        );
    }

    private void mockActiveUser() {

        User account = mock(User.class);

        when(account.isActive()).thenReturn(true);
        when(account.getId()).thenReturn(10L);

        when(userRepository.findByEmail(EMAIL))
                .thenReturn(Optional.of(account));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ADMIN",
            "MANAGER",
            "INVENTORY_STAFF"
    })
    void shouldAllowAuthorizedUsersToViewPurchases(
            String role
    ) throws Exception {

        when(purchaseService.getAllPurchases())
                .thenReturn(List.of(response("DRAFT")));

        mockMvc.perform(
                        get(BASE_URL)
                                .with(user(EMAIL).roles(role))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].status").value("DRAFT"));

        verify(purchaseService).getAllPurchases();
    }

    @Test
    void shouldRejectCashierViewingPurchases()
            throws Exception {

        mockMvc.perform(
                        get(BASE_URL)
                                .with(user(EMAIL).roles("CASHIER"))
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(purchaseService);
    }

    @Test
    void shouldRejectUnauthenticatedRequest()
            throws Exception {

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isForbidden());

        verifyNoInteractions(purchaseService);
    }

    @Test
    void shouldReturnPurchaseById()
            throws Exception {

        when(purchaseService.getPurchaseById(1L))
                .thenReturn(response("DRAFT"));

        mockMvc.perform(
                        get(BASE_URL + "/1")
                                .with(user(EMAIL).roles("ADMIN"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.total").value(450.00));

        verify(purchaseService).getPurchaseById(1L);
    }

    @Test
    void shouldReturnNotFoundForMissingPurchase()
            throws Exception {

        when(purchaseService.getPurchaseById(99L))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Purchase not found"
                ));

        mockMvc.perform(
                        get(BASE_URL + "/99")
                                .with(user(EMAIL).roles("ADMIN"))
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldSearchPurchases()
            throws Exception {

        when(purchaseService.searchPurchases(
                "PUR",
                1L,
                PurchaseStatus.DRAFT,
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30)
        )).thenReturn(List.of(response("DRAFT")));

        mockMvc.perform(
                        get(BASE_URL + "/search")
                                .with(user(EMAIL).roles("MANAGER"))
                                .param("purchaseNumber", "PUR")
                                .param("supplierId", "1")
                                .param("status", "DRAFT")
                                .param("fromDate", "2026-11-01")
                                .param("toDate", "2026-11-30")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("DRAFT"));

        verify(purchaseService).searchPurchases(
                "PUR",
                1L,
                PurchaseStatus.DRAFT,
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30)
        );
    }

    @Test
    void shouldRejectInvalidSearchStatus()
            throws Exception {

        mockMvc.perform(
                        get(BASE_URL + "/search")
                                .with(user(EMAIL).roles("ADMIN"))
                                .param("status", "INVALID")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(purchaseService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ADMIN",
            "MANAGER",
            "INVENTORY_STAFF"
    })
    void shouldCreatePurchaseForAuthorizedUsers(
            String role
    ) throws Exception {

        mockActiveUser();

        when(purchaseService.createPurchase(
                any(CreatePurchaseRequest.class),
                eq(10L)
        )).thenReturn(response("DRAFT"));

        mockMvc.perform(
                        post(BASE_URL)
                                .with(user(EMAIL).roles(role))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_REQUEST)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.total").value(450.00));

        verify(purchaseService).createPurchase(
                any(CreatePurchaseRequest.class),
                eq(10L)
        );
    }

    @Test
    void shouldRejectCashierCreatingPurchase()
            throws Exception {

        mockMvc.perform(
                        post(BASE_URL)
                                .with(user(EMAIL).roles("CASHIER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_REQUEST)
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(purchaseService);
    }

    @Test
    void shouldRejectInvalidPurchaseRequest()
            throws Exception {

        mockMvc.perform(
                        post(BASE_URL)
                                .with(user(EMAIL).roles("ADMIN"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(purchaseService);
    }

    @Test
    void shouldRejectInvalidPurchaseQuantity()
            throws Exception {

        String invalidRequest = VALID_REQUEST.replace(
                "\"quantity\": 5",
                "\"quantity\": 0"
        );

        mockMvc.perform(
                        post(BASE_URL)
                                .with(user(EMAIL).roles("ADMIN"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidRequest)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(purchaseService);
    }

    @Test
    void shouldRejectInactivePurchaseCreator()
            throws Exception {

        User account = mock(User.class);

        when(account.isActive()).thenReturn(false);

        when(userRepository.findByEmail(EMAIL))
                .thenReturn(Optional.of(account));

        mockMvc.perform(
                        post(BASE_URL)
                                .with(user(EMAIL).roles("ADMIN"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_REQUEST)
                )
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(purchaseService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "MANAGER"})
    void shouldAllowAuthorizedPurchaseCancellation(
            String role
    ) throws Exception {

        when(purchaseService.cancelPurchase(1L))
                .thenReturn(response("CANCELLED"));

        mockMvc.perform(
                        post(BASE_URL + "/1/cancel")
                                .with(user(EMAIL).roles(role))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(purchaseService).cancelPurchase(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "CASHIER",
            "INVENTORY_STAFF"
    })
    void shouldRejectUnauthorizedPurchaseCancellation(
            String role
    ) throws Exception {

        mockMvc.perform(
                        post(BASE_URL + "/1/cancel")
                                .with(user(EMAIL).roles(role))
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(purchaseService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "MANAGER"})
    void shouldAllowAuthorizedPurchaseReceiving(
            String role
    ) throws Exception {

        when(receivingService.receivePurchase(1L))
                .thenReturn(response("RECEIVED"));

        mockMvc.perform(
                        post(BASE_URL + "/1/receive")
                                .with(user(EMAIL).roles(role))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        verify(receivingService).receivePurchase(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "CASHIER",
            "INVENTORY_STAFF"
    })
    void shouldRejectUnauthorizedPurchaseReceiving(
            String role
    ) throws Exception {

        mockMvc.perform(
                        post(BASE_URL + "/1/receive")
                                .with(user(EMAIL).roles(role))
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(receivingService);
    }

    @Test
    void shouldReturnConflictForDuplicateReceiving()
            throws Exception {

        when(receivingService.receivePurchase(1L))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Only draft purchases can be received"
                ));

        mockMvc.perform(
                        post(BASE_URL + "/1/receive")
                                .with(user(EMAIL).roles("ADMIN"))
                )
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturnServiceUnavailableWhenInventoryIsMissing()
            throws Exception {

        when(receivingService.receivePurchase(1L))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Inventory receiving integration is unavailable"
                ));

        mockMvc.perform(
                        post(BASE_URL + "/1/receive")
                                .with(user(EMAIL).roles("ADMIN"))
                )
                .andExpect(status().isServiceUnavailable());
    }
}