package com.mysillydreams.orderapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.orderapi.dto.CreateOrderRequest;
import com.mysillydreams.orderapi.dto.LineItemDto;
import com.mysillydreams.orderapi.service.OrderApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@ContextConfiguration(classes = OrderController.class) // Specify controller for context
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderApiService orderApiService;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID customerId;
    private UUID orderId;
    private CreateOrderRequest createOrderRequest;
    private String idempotencyKey;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        idempotencyKey = UUID.randomUUID().toString();

        LineItemDto lineItem = new LineItemDto(UUID.randomUUID(), 1, BigDecimal.TEN);
        createOrderRequest = new CreateOrderRequest(null, List.of(lineItem), "USD");
    }

    private Jwt createMockJwt(String subject, String... roles) {
        Jwt.Builder jwtBuilder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .claim("scope", "message.read message.write") // example scopes
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));

        if (roles.length > 0) {
            // Keycloak typically puts roles in 'realm_access.roles' or 'resource_access.<client_id>.roles'
            // Spring Security's JwtAuthenticationConverter by default looks for 'scope' or 'scp'.
            // For hasRole("USER") to work with Keycloak roles, a custom JwtAuthenticationConverter
            // might be needed to map Keycloak roles to Spring Security authorities.
            // For this test, we'll add authorities directly as if they were processed.
            // The KeycloakConfig in the main code should handle this mapping if necessary.
            // Here, we simulate the authorities that would result from Keycloak role "USER".
        }
        return jwtBuilder.build();
    }

    @Test
    void createOrder_validRequest_shouldReturnAccepted() throws Exception {
        when(orderApiService.createOrder(any(CreateOrderRequest.class), eq(idempotencyKey)))
                .thenReturn(orderId);

        mockMvc.perform(post("/orders")
                        .with(jwt().jwt(j -> j.subject(customerId.toString())).authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createOrderRequest)))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()));
    }

    @Test
    void createOrder_missingIdempotencyKey_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/orders")
                        .with(jwt().jwt(j -> j.subject(customerId.toString())).authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createOrderRequest)))
                .andExpect(status().isBadRequest()); // This is based on Spring's default handling for missing headers.
                                                    // The IdempotencyFilter will actually handle this.
                                                    // To test the filter's behavior, an integration test is better.
                                                    // For a strict controller unit test, this checks if the header is declared.
    }

    @Test
    void createOrder_invalidRequestBody_shouldReturnBadRequest() throws Exception {
        CreateOrderRequest invalidRequest = new CreateOrderRequest(null, Collections.emptyList(), "US"); // Invalid currency

        mockMvc.perform(post("/orders")
                        .with(jwt().jwt(j -> j.subject(customerId.toString())).authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }


    @Test
    void cancelOrder_validRequest_shouldReturnAccepted() throws Exception {
        UUID orderToCancel = UUID.randomUUID();
        String reason = "No longer needed";

        // Mock the service call for cancelOrder if it's void
        // Mockito.doNothing().when(orderApiService).cancelOrder(orderToCancel, reason);

        mockMvc.perform(put("/orders/{id}/cancel", orderToCancel)
                        .with(jwt().jwt(j -> j.subject(customerId.toString())).authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .param("reason", reason))
                .andExpect(status().isAccepted());
    }

    @Test
    void createOrder_unauthorizedUser_shouldReturnForbidden() throws Exception {
        // Simulate a JWT without the required "USER" role/authority
        mockMvc.perform(post("/orders")
                        .with(jwt().jwt(j -> j.subject(customerId.toString()))) // No specific authorities/roles granted
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createOrderRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelOrder_unauthorizedUser_shouldReturnForbidden() throws Exception {
        UUID orderToCancel = UUID.randomUUID();
        String reason = "No longer needed";

        mockMvc.perform(put("/orders/{id}/cancel", orderToCancel)
                        .with(jwt().jwt(j -> j.subject(customerId.toString()))) // No specific authorities/roles granted
                        .param("reason", reason))
                .andExpect(status().isForbidden());
    }
}
