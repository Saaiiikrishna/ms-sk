package com.mysillydreams.inventoryapi.service;

import com.mysillydreams.inventoryapi.domain.StockLevel;
import com.mysillydreams.inventoryapi.dto.AdjustStockRequest;
import com.mysillydreams.inventoryapi.dto.ReservationRequestDto;
import com.mysillydreams.inventoryapi.dto.StockLevelDto;
import com.mysillydreams.inventoryapi.repository.StockLevelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private StockLevelRepository stockLevelRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    private final String testTopic = "order.reservation.requested";

    @BeforeEach
    void setUp() {
        // Use ReflectionTestUtils to set the @Value field
        ReflectionTestUtils.setField(inventoryService, "reservationTopic", testTopic);
    }

    @Test
    void getStock_existingSku_returnsDto() {
        String sku = "SKU123";
        StockLevel stockLevel = new StockLevel(sku, 100, 10, Instant.now());
        when(stockLevelRepository.findById(sku)).thenReturn(Optional.of(stockLevel));

        StockLevelDto result = inventoryService.getStock(sku);

        assertNotNull(result);
        assertEquals(sku, result.getSku());
        assertEquals(100, result.getAvailable());
        assertEquals(10, result.getReserved());
        verify(stockLevelRepository).findById(sku);
    }

    @Test
    void getStock_nonExistingSku_returnsDefaultDto() {
        String sku = "SKU-NON-EXIST";
        when(stockLevelRepository.findById(sku)).thenReturn(Optional.empty());

        StockLevelDto result = inventoryService.getStock(sku);

        assertNotNull(result);
        assertEquals(sku, result.getSku());
        assertEquals(0, result.getAvailable());
        assertEquals(0, result.getReserved());
        verify(stockLevelRepository).findById(sku);
    }

    @Test
    void adjustStock_existingSku_updatesStock() {
        String sku = "SKU123";
        int initialAvailable = 100;
        int delta = -20;
        StockLevel stockLevel = new StockLevel(sku, initialAvailable, 10, Instant.now());
        when(stockLevelRepository.findById(sku)).thenReturn(Optional.of(stockLevel));
        when(stockLevelRepository.save(any(StockLevel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdjustStockRequest request = new AdjustStockRequest(sku, delta);
        inventoryService.adjustStock(request);

        assertEquals(initialAvailable + delta, stockLevel.getAvailable());
        verify(stockLevelRepository).findById(sku);
        verify(stockLevelRepository).save(stockLevel);
    }

    @Test
    void adjustStock_nonExistingSku_createsAndAdjustsStock() {
        String sku = "SKU-NEW";
        int delta = 50;
        when(stockLevelRepository.findById(sku)).thenReturn(Optional.empty());
        // Mock save to capture the argument or return it
        when(stockLevelRepository.save(any(StockLevel.class))).thenAnswer(invocation -> {
            StockLevel savedLevel = invocation.getArgument(0);
            // Simulate that save operation populates the updatedAt field
            // savedLevel.setUpdatedAt(Instant.now()); // Not necessary to mock this if @UpdateTimestamp works
            return savedLevel;
        });


        AdjustStockRequest request = new AdjustStockRequest(sku, delta);
        inventoryService.adjustStock(request);

        // We need to capture the argument to verify its state
        verify(stockLevelRepository).findById(sku);
        verify(stockLevelRepository).save(argThat(savedLevel ->
            savedLevel.getSku().equals(sku) &&
            savedLevel.getAvailable() == delta &&
            savedLevel.getReserved() == 0
        ));
    }

     @Test
    void adjustStock_positiveDelta_increasesAvailable() {
        String sku = "SKU-INC";
        StockLevel stockLevel = new StockLevel(sku, 50, 5, Instant.now());
        when(stockLevelRepository.findById(sku)).thenReturn(Optional.of(stockLevel));

        AdjustStockRequest request = new AdjustStockRequest(sku, 25);
        inventoryService.adjustStock(request);

        assertEquals(75, stockLevel.getAvailable());
        verify(stockLevelRepository).save(stockLevel);
    }

    @Test
    void reserve_sendsToKafka() {
        UUID orderId = UUID.randomUUID();
        String sku = "SKU-RESERVE";
        int quantity = 5;
        ReservationRequestDto.LineItem lineItem = new ReservationRequestDto.LineItem(sku, quantity);
        ReservationRequestDto request = new ReservationRequestDto(orderId, Collections.singletonList(lineItem));

        inventoryService.reserve(request);

        verify(kafkaTemplate).send(eq(testTopic), eq(orderId.toString()), eq(request));
    }
}
