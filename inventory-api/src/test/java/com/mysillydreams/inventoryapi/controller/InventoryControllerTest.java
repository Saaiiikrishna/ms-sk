package com.mysillydreams.inventoryapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.inventoryapi.dto.AdjustStockRequest;
import com.mysillydreams.inventoryapi.dto.ReservationRequestDto;
import com.mysillydreams.inventoryapi.dto.StockLevelDto;
import com.mysillydreams.inventoryapi.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.is;


// Specify the controller class to test for focused slicing
// Disable Keycloak security for this controller test slice if not testing security rules here
// This can be done by not importing KeycloakWebSecurityConfigurerAdapter or using custom test annotations
// For WebMvcTest, Spring Security is auto-configured. We need to ensure Keycloak parts are either mocked or disabled
// if they interfere and we are not testing security rules directly.
// Often, @WithMockUser or similar is used if security is active.
// Since KeycloakConfig is now active, we need to provide a mock user with appropriate roles or disable security.
// Easiest for now is to assume security is handled by KeycloakConfig and not re-test it here,
// or provide a way to disable it for this slice if it causes issues (e.g. via profiles in application-test.yml)

@WebMvcTest(InventoryController.class) // Specify controller for focused test slice
@ActiveProfiles("test") // Ensure application-test.yml is loaded (e.g. to disable Keycloak if configured there)
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventoryService inventoryService; // Mock the service layer

    @Autowired
    private ObjectMapper objectMapper; // For serializing request bodies

    @Test
    // @WithMockUser(roles = {"INVENTORY"}) // Example if security was active and needed for this endpoint
    void getStock_validSku_returnsStockLevelDto() throws Exception {
        String sku = "SKU123";
        StockLevelDto stockLevelDto = new StockLevelDto(sku, 100, 10);
        when(inventoryService.getStock(sku)).thenReturn(stockLevelDto);

        mockMvc.perform(get("/inventory/{sku}", sku)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku", is(sku)))
                .andExpect(jsonPath("$.available", is(100)))
                .andExpect(jsonPath("$.reserved", is(10)));

        verify(inventoryService).getStock(sku);
    }

    @Test
    // @WithMockUser(roles = {"INVENTORY"})
    void adjust_validRequest_returnsOk() throws Exception {
        AdjustStockRequest request = new AdjustStockRequest("SKU123", -10);
        doNothing().when(inventoryService).adjustStock(any(AdjustStockRequest.class));

        mockMvc.perform(post("/inventory/adjust")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(inventoryService).adjustStock(any(AdjustStockRequest.class));
    }

    @Test
    // @WithMockUser(roles = {"INVENTORY"})
    void adjust_invalidRequest_returnsBadRequest() throws Exception {
        // SKU is blank
        AdjustStockRequest request = new AdjustStockRequest("", -10);

        mockMvc.perform(post("/inventory/adjust")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest()); // Due to @Valid and @NotBlank on DTO

        // Delta is null
        AdjustStockRequest request2 = new AdjustStockRequest("SKU123", null);
         mockMvc.perform(post("/inventory/adjust")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isBadRequest());
    }


    @Test
    // @WithMockUser(roles = {"INVENTORY"})
    void reserve_validRequest_returnsAccepted() throws Exception {
        UUID orderId = UUID.randomUUID();
        ReservationRequestDto.LineItem lineItem = new ReservationRequestDto.LineItem("SKU123", 5);
        ReservationRequestDto request = new ReservationRequestDto(orderId, Collections.singletonList(lineItem));

        doNothing().when(inventoryService).reserve(any(ReservationRequestDto.class));

        mockMvc.perform(post("/inventory/reserve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        verify(inventoryService).reserve(any(ReservationRequestDto.class));
    }

    @Test
    // @WithMockUser(roles = {"INVENTORY"})
    void reserve_invalidRequest_returnsBadRequest() throws Exception {
        // OrderId is null
        ReservationRequestDto request1 = new ReservationRequestDto(null, Collections.singletonList(new ReservationRequestDto.LineItem("SKU123", 1)));
        mockMvc.perform(post("/inventory/reserve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isBadRequest());

        // Items list is empty
        ReservationRequestDto request2 = new ReservationRequestDto(UUID.randomUUID(), Collections.emptyList());
        mockMvc.perform(post("/inventory/reserve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isBadRequest());

        // LineItem SKU is blank
        ReservationRequestDto.LineItem invalidItem = new ReservationRequestDto.LineItem("", 1);
        ReservationRequestDto request3 = new ReservationRequestDto(UUID.randomUUID(), Collections.singletonList(invalidItem));
         mockMvc.perform(post("/inventory/reserve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request3)))
                .andExpect(status().isBadRequest());

        // LineItem quantity is less than 1
        ReservationRequestDto.LineItem invalidItem2 = new ReservationRequestDto.LineItem("SKU123", 0);
        ReservationRequestDto request4 = new ReservationRequestDto(UUID.randomUUID(), Collections.singletonList(invalidItem2));
         mockMvc.perform(post("/inventory/reserve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request4)))
                .andExpect(status().isBadRequest());
    }
}
