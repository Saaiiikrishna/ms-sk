package com.mysillydreams.userservice.web.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.userservice.domain.inventory.TransactionType;
import com.mysillydreams.userservice.dto.inventory.InventoryItemDto;
import com.mysillydreams.userservice.dto.inventory.StockAdjustmentRequest;
import com.mysillydreams.userservice.service.inventory.InventoryManagementService;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;


import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InventoryController.class)
public class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventoryManagementService mockInventoryManagementService;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID testProfileId;
    private UUID testItemId;
    private InventoryItemDto testItemDto;
    private StockAdjustmentRequest testAdjustmentRequest;

    @BeforeEach
    void setUp() {
        testProfileId = UUID.randomUUID();
        testItemId = UUID.randomUUID();

        testItemDto = new InventoryItemDto();
        testItemDto.setId(testItemId);
        testItemDto.setSku("ITEM001");
        testItemDto.setName("Super Item");
        testItemDto.setQuantityOnHand(10);
        testItemDto.setReorderLevel(5);

        testAdjustmentRequest = new StockAdjustmentRequest();
        testAdjustmentRequest.setType(TransactionType.RECEIVE);
        testAdjustmentRequest.setQuantity(5);
    }

    @Test
    void addItem_success() throws Exception {
        given(mockInventoryManagementService.addItem(eq(testProfileId), any(InventoryItemDto.class)))
                .willReturn(testItemDto);

        mockMvc.perform(post("/inventory/items")
                        .header("X-Inventory-Profile-Id", testProfileId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testItemDto))) // Send DTO for creation
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku", is(testItemDto.getSku())))
                .andExpect(jsonPath("$.name", is(testItemDto.getName())));
    }

    @Test
    void addItem_profileNotFound_returnsNotFound() throws Exception {
        given(mockInventoryManagementService.addItem(eq(testProfileId), any(InventoryItemDto.class)))
            .willThrow(new EntityNotFoundException("InventoryProfile not found"));

        mockMvc.perform(post("/inventory/items")
                        .header("X-Inventory-Profile-Id", testProfileId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testItemDto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("InventoryProfile not found")));
    }

    @Test
    void addItem_duplicateSku_returnsBadRequest() throws Exception {
         given(mockInventoryManagementService.addItem(eq(testProfileId), any(InventoryItemDto.class)))
            .willThrow(new IllegalArgumentException("SKU already exists"));

        mockMvc.perform(post("/inventory/items")
                        .header("X-Inventory-Profile-Id", testProfileId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testItemDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("SKU already exists")));
    }


    @Test
    void listItems_success() throws Exception {
        given(mockInventoryManagementService.listItemsByProfileId(testProfileId))
                .willReturn(List.of(testItemDto));

        mockMvc.perform(get("/inventory/items")
                        .header("X-Inventory-Profile-Id", testProfileId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].sku", is(testItemDto.getSku())));
    }

    @Test
    void listItems_profileNotFound_returnsNotFound() throws Exception {
        given(mockInventoryManagementService.listItemsByProfileId(testProfileId))
            .willThrow(new EntityNotFoundException("InventoryProfile not found"));

        mockMvc.perform(get("/inventory/items")
                        .header("X-Inventory-Profile-Id", testProfileId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("InventoryProfile not found")));
    }

    @Test
    void listItems_noItems_returnsEmptyList() throws Exception {
        given(mockInventoryManagementService.listItemsByProfileId(testProfileId))
                .willReturn(Collections.emptyList());

        mockMvc.perform(get("/inventory/items")
                        .header("X-Inventory-Profile-Id", testProfileId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }


    @Test
    void adjustStock_success() throws Exception {
        testItemDto.setQuantityOnHand(testItemDto.getQuantityOnHand() + testAdjustmentRequest.getQuantity()); // Simulate updated DTO
        given(mockInventoryManagementService.adjustStock(eq(testItemId), any(StockAdjustmentRequest.class)))
                .willReturn(testItemDto);

        mockMvc.perform(post("/inventory/items/{itemId}/adjust", testItemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testAdjustmentRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityOnHand", is(testItemDto.getQuantityOnHand())));
    }

    @Test
    void adjustStock_itemNotFound_returnsNotFound() throws Exception {
        given(mockInventoryManagementService.adjustStock(eq(testItemId), any(StockAdjustmentRequest.class)))
                .willThrow(new EntityNotFoundException("InventoryItem not found"));

        mockMvc.perform(post("/inventory/items/{itemId}/adjust", testItemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testAdjustmentRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("InventoryItem not found")));
    }

    @Test
    void adjustStock_insufficientStock_returnsBadRequest() throws Exception {
        given(mockInventoryManagementService.adjustStock(eq(testItemId), any(StockAdjustmentRequest.class)))
                .willThrow(new IllegalArgumentException("Insufficient stock"));

        mockMvc.perform(post("/inventory/items/{itemId}/adjust", testItemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testAdjustmentRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Insufficient stock")));
    }

    @Test
    void addItem_missingHeader_returnsBadRequest() throws Exception {
         mockMvc.perform(post("/inventory/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testItemDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listItems_missingHeader_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/inventory/items"))
               .andExpect(status().isBadRequest());
    }
}
