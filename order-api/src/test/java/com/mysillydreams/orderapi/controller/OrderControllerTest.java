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

import com.mysillydreams.orderapi.dto.ApiError;
import com.mysillydreams.orderapi.exception.GlobalExceptionHandler; // Import to ensure it's on classpath for test context
import org.springframework.context.annotation.Import;
import org.springframework.kafka.KafkaException;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.mysillydreams.orderapi.config.KeycloakConfig; // To satisfy WebMvcTest dependencies if security is active

@WebMvcTest(OrderController.class)
// Import GlobalExceptionHandler to make it active for this slice test.
// Also import KeycloakConfig because WebMvcTest tries to load security configurations.
// If KeycloakConfig is not imported, auto-configuration might fail or security might not be applied as expected.
// However, for testing specific controller advice, sometimes disabling security entirely for the test slice is easier
// if the advice itself isn't security-dependent. For now, let's import it.
@Import({GlobalExceptionHandler.class, KeycloakConfig.class})
// No need for @ContextConfiguration(classes = OrderController.class) when using @WebMvcTest(OrderController.class)
// and importing necessary additional configs like GlobalExceptionHandler.
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

    @Test
    void createOrder_whenServiceThrowsKafkaException_shouldReturnServiceUnavailable() throws Exception {
        when(orderApiService.createOrder(any(CreateOrderRequest.class), eq(idempotencyKey)))
                .thenThrow(new KafkaException("Simulated Kafka is down"));

        mockMvc.perform(post("/orders")
                        .with(jwt().jwt(j -> j.subject(customerId.toString())).authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createOrderRequest)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is(503)))
                .andExpect(jsonPath("$.error", is("KAFKA_ERROR")))
                .andExpect(jsonPath("$.message", is("Error communicating with Kafka: Simulated Kafka is down")))
                .andExpect(jsonPath("$.path", is("/orders")));
    }

    @Test
    void createOrder_whenRequestBodyIsMalformed_shouldReturnBadRequest() throws Exception {
        String malformedJson = "{\"items\":[{\"productId\":\"abc\",\"quantity\":1,\"price\":10.0}],\"currency\":\"USD\""; // Missing closing brace

        mockMvc.perform(post("/orders")
                        .with(jwt().jwt(j -> j.subject(customerId.toString())).authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("MALFORMED_REQUEST")))
                // Message for HttpMessageNotReadableException can be verbose and vary, so check for existence or partial match.
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path", is("/orders")));
    }

    @Test
    void createOrder_whenMissingIdempotencyKeyHeader_shouldReturnBadRequestFromHandler() throws Exception {
        // This test now relies on the GlobalExceptionHandler for MissingRequestHeaderException
        // The IdempotencyFilter also checks this, but if the filter was somehow bypassed or another controller
        // required a header, this handler would catch it.
        mockMvc.perform(post("/orders")
                        .with(jwt().jwt(j -> j.subject(customerId.toString())).authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        // No "Idempotency-Key" header
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createOrderRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("MISSING_HEADER"))) // This comes from our handler
                .andExpect(jsonPath("$.message", is("Required request header 'Idempotency-Key' is not present.")));
    }


    @Test
    void cancelOrder_whenServiceThrowsGenericException_shouldReturnInternalServerError() throws Exception {
        UUID orderToCancel = UUID.randomUUID();
        String reason = "Test reason";

        doThrow(new RuntimeException("Unexpected internal error")).when(orderApiService).cancelOrder(orderToCancel, reason);

        mockMvc.perform(put("/orders/{id}/cancel", orderToCancel)
                        .with(jwt().jwt(j -> j.subject(customerId.toString())).authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .param("reason", reason))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is(500)))
                .andExpect(jsonPath("$.error", is("INTERNAL_SERVER_ERROR")))
                .andExpect(jsonPath("$.message", is("An unexpected error occurred: Unexpected internal error")))
                .andExpect(jsonPath("$.path", is("/orders/" + orderToCancel + "/cancel")));
    }
}
