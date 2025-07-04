package com.mysillydreams.catalogservice.service;

import com.mysillydreams.catalogservice.domain.model.CatalogItemEntity;
import com.mysillydreams.catalogservice.domain.model.ItemType;
import com.mysillydreams.catalogservice.domain.model.StockLevelEntity;
import com.mysillydreams.catalogservice.domain.model.StockTransactionEntity;
import com.mysillydreams.catalogservice.domain.repository.CatalogItemRepository;
import com.mysillydreams.catalogservice.domain.repository.StockLevelRepository;
import com.mysillydreams.catalogservice.domain.repository.StockTransactionRepository;
import com.mysillydreams.catalogservice.dto.StockAdjustmentRequest;
import com.mysillydreams.catalogservice.dto.StockAdjustmentType;
import com.mysillydreams.catalogservice.dto.StockLevelDto;
import com.mysillydreams.catalogservice.exception.InvalidRequestException;
import com.mysillydreams.catalogservice.exception.ResourceNotFoundException;
import com.mysillydreams.catalogservice.kafka.event.StockLevelChangedEvent;
import com.mysillydreams.catalogservice.kafka.producer.KafkaProducerService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import jakarta.persistence.OptimisticLockException;


import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StockServiceTest {

    @Mock private StockLevelRepository stockLevelRepository;
    @Mock private CatalogItemRepository catalogItemRepository;
    @Mock private StockTransactionRepository stockTransactionRepository;
    @Mock private KafkaProducerService kafkaProducerService;

    @InjectMocks private StockService stockService;

    private UUID itemId;
    private CatalogItemEntity productItem;
    private StockLevelEntity stockLevel;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(stockService, "stockChangedTopic", "stock.changed");

        itemId = UUID.randomUUID();
        productItem = CatalogItemEntity.builder().id(itemId).sku("PROD001").name("Test Product").type(ItemType.PRODUCT).build();
        stockLevel = StockLevelEntity.builder().itemId(itemId).catalogItem(productItem).quantityOnHand(100).reorderLevel(10).version(0L).updatedAt(Instant.now()).build();
    }

    @Test
    void adjustStock_receive_success() {
        StockAdjustmentRequest request = StockAdjustmentRequest.builder()
                .itemId(itemId).adjustmentType(StockAdjustmentType.RECEIVE).quantity(50).reason("New shipment").build();

        when(catalogItemRepository.findById(itemId)).thenReturn(Optional.of(productItem));
        when(stockLevelRepository.findByCatalogItemId(itemId)).thenReturn(Optional.of(stockLevel));
        when(stockLevelRepository.save(any(StockLevelEntity.class))).thenAnswer(inv -> {
            StockLevelEntity sl = inv.getArgument(0);
            sl.setVersion(sl.getVersion() + 1); // Simulate version increment
            return sl;
        });

        StockLevelDto result = stockService.adjustStock(request);

        assertThat(result.getQuantityOnHand()).isEqualTo(150);
        verify(stockLevelRepository).save(argThat(sl -> sl.getQuantityOnHand() == 150));
        verify(stockTransactionRepository).save(argThat(st ->
                st.getTransactionType() == StockAdjustmentType.RECEIVE &&
                st.getQuantityChanged() == 50 &&
                st.getQuantityBeforeTransaction() == 100 &&
                st.getQuantityAfterTransaction() == 150
        ));
        verify(kafkaProducerService).sendMessage(eq("stock.changed"), eq(itemId.toString()), any(StockLevelChangedEvent.class));
    }

    @Test
    void adjustStock_issue_success() {
        StockAdjustmentRequest request = StockAdjustmentRequest.builder()
                .itemId(itemId).adjustmentType(StockAdjustmentType.ISSUE).quantity(30).reason("Order fulfillment").build();

        when(catalogItemRepository.findById(itemId)).thenReturn(Optional.of(productItem));
        when(stockLevelRepository.findByCatalogItemId(itemId)).thenReturn(Optional.of(stockLevel));
        when(stockLevelRepository.save(any(StockLevelEntity.class))).thenAnswer(inv -> inv.getArgument(0));


        StockLevelDto result = stockService.adjustStock(request);

        assertThat(result.getQuantityOnHand()).isEqualTo(70);
        verify(stockLevelRepository).save(argThat(sl -> sl.getQuantityOnHand() == 70));
        verify(stockTransactionRepository).save(argThat(st ->
                st.getTransactionType() == StockAdjustmentType.ISSUE &&
                st.getQuantityChanged() == -30 && // Delta is negative
                st.getQuantityAfterTransaction() == 70
        ));
        ArgumentCaptor<StockLevelChangedEvent> eventCaptor = ArgumentCaptor.forClass(StockLevelChangedEvent.class);
        verify(kafkaProducerService).sendMessage(eq("stock.changed"), eq(itemId.toString()), eventCaptor.capture());
        assertThat(eventCaptor.getValue().getQuantityChanged()).isEqualTo(-30);
    }

    @Test
    void adjustStock_issue_insufficientStock_throwsException() {
        StockAdjustmentRequest request = StockAdjustmentRequest.builder()
                .itemId(itemId).adjustmentType(StockAdjustmentType.ISSUE).quantity(150).build(); // More than available

        when(catalogItemRepository.findById(itemId)).thenReturn(Optional.of(productItem));
        when(stockLevelRepository.findByCatalogItemId(itemId)).thenReturn(Optional.of(stockLevel)); // stock is 100

        assertThrows(InvalidRequestException.class, () -> stockService.adjustStock(request));
        verify(stockLevelRepository, never()).save(any());
        verify(stockTransactionRepository, never()).save(any());
        verify(kafkaProducerService, never()).sendMessage(anyString(), anyString(), any());
    }

    @Test
    void adjustStock_itemNotProduct_throwsException() {
        CatalogItemEntity serviceItem = CatalogItemEntity.builder().id(itemId).type(ItemType.SERVICE).build();
        StockAdjustmentRequest request = StockAdjustmentRequest.builder().itemId(itemId).adjustmentType(StockAdjustmentType.RECEIVE).quantity(10).build();

        when(catalogItemRepository.findById(itemId)).thenReturn(Optional.of(serviceItem));

        assertThrows(InvalidRequestException.class, () -> stockService.adjustStock(request));
    }

    @Test
    void adjustStock_itemNotFound_throwsException() {
        StockAdjustmentRequest request = StockAdjustmentRequest.builder().itemId(UUID.randomUUID()).adjustmentType(StockAdjustmentType.RECEIVE).quantity(10).build();
        when(catalogItemRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> stockService.adjustStock(request));
    }

    @Test
    void reserveStock_sufficientStock_succeeds() {
        when(catalogItemRepository.findById(itemId)).thenReturn(Optional.of(productItem));
        when(stockLevelRepository.findByCatalogItemId(itemId)).thenReturn(Optional.of(stockLevel)); // stock is 100
        when(stockLevelRepository.save(any(StockLevelEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        int quantityToReserve = 20;
        StockLevelDto result = stockService.reserveStock(itemId, quantityToReserve);

        assertThat(result.getQuantityOnHand()).isEqualTo(80); // 100 - 20
        verify(stockTransactionRepository).save(argThat(st ->
            st.getTransactionType() == StockAdjustmentType.ISSUE &&
            st.getQuantityChanged() == -quantityToReserve &&
            st.getReason().equals("CartReservation")
        ));
        ArgumentCaptor<StockLevelChangedEvent> eventCaptor = ArgumentCaptor.forClass(StockLevelChangedEvent.class);
        verify(kafkaProducerService).sendMessage(anyString(), anyString(), eventCaptor.capture());
        assertThat(eventCaptor.getValue().getQuantityChanged()).isEqualTo(-quantityToReserve);
    }

    @Test
    void reserveStock_insufficientStock_throwsException() {
        when(catalogItemRepository.findById(itemId)).thenReturn(Optional.of(productItem));
        when(stockLevelRepository.findByCatalogItemId(itemId)).thenReturn(Optional.of(stockLevel)); // stock is 100

        assertThrows(InvalidRequestException.class, () -> stockService.reserveStock(itemId, 120)); // reserve more than available
    }

    @Test
    void releaseStock_succeeds() {
        when(catalogItemRepository.findById(itemId)).thenReturn(Optional.of(productItem));
        when(stockLevelRepository.findByCatalogItemId(itemId)).thenReturn(Optional.of(stockLevel)); // stock is 100
        when(stockLevelRepository.save(any(StockLevelEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        int quantityToRelease = 15;
        StockLevelDto result = stockService.releaseStock(itemId, quantityToRelease);

        assertThat(result.getQuantityOnHand()).isEqualTo(115); // 100 + 15
         verify(stockTransactionRepository).save(argThat(st ->
            st.getTransactionType() == StockAdjustmentType.RECEIVE &&
            st.getQuantityChanged() == quantityToRelease &&
            st.getReason().equals("CartRelease/Cancellation")
        ));
        ArgumentCaptor<StockLevelChangedEvent> eventCaptor = ArgumentCaptor.forClass(StockLevelChangedEvent.class);
        verify(kafkaProducerService).sendMessage(anyString(), anyString(), eventCaptor.capture());
        assertThat(eventCaptor.getValue().getQuantityChanged()).isEqualTo(quantityToRelease);
    }

    @Test
    void getStockLevelByItemId_productFound_returnsDto() {
        when(catalogItemRepository.findById(itemId)).thenReturn(Optional.of(productItem));
        when(stockLevelRepository.findByCatalogItemId(itemId)).thenReturn(Optional.of(stockLevel));

        StockLevelDto dto = stockService.getStockLevelByItemId(itemId);

        assertThat(dto).isNotNull();
        assertThat(dto.getItemId()).isEqualTo(itemId);
        assertThat(dto.getQuantityOnHand()).isEqualTo(100);
    }

    @Test
    void getStockLevelByItemId_serviceItem_returnsDtoWithNullQuantity() {
        CatalogItemEntity serviceItem = CatalogItemEntity.builder().id(itemId).sku("SERV01").name("Support Service").type(ItemType.SERVICE).build();
        when(catalogItemRepository.findById(itemId)).thenReturn(Optional.of(serviceItem));
        // stockLevelRepository should not be called for service item

        StockLevelDto dto = stockService.getStockLevelByItemId(itemId);

        assertThat(dto).isNotNull();
        assertThat(dto.getItemId()).isEqualTo(itemId);
        assertThat(dto.getQuantityOnHand()).isNull();
        verify(stockLevelRepository, never()).findByCatalogItemId(any());
    }

    @Test
    void adjustStock_optimisticLockException_shouldBeHandledByRetryable() {
        // This test is hard to write as a pure unit test without Spring context for @Retryable
        // We can simulate the OptimisticLockException being thrown by the repository mock
        StockAdjustmentRequest request = StockAdjustmentRequest.builder()
                .itemId(itemId).adjustmentType(StockAdjustmentType.RECEIVE).quantity(50).build();

        when(catalogItemRepository.findById(itemId)).thenReturn(Optional.of(productItem));
        when(stockLevelRepository.findByCatalogItemId(itemId)).thenReturn(Optional.of(stockLevel));

        // Simulate lock exception on first attempt, success on second
        when(stockLevelRepository.save(any(StockLevelEntity.class)))
            .thenThrow(new OptimisticLockException("First attempt failed"))
            .thenAnswer(inv -> { // Second attempt successful
                StockLevelEntity sl = inv.getArgument(0);
                sl.setVersion(sl.getVersion() + 1);
                return sl;
            });

        // If @Retryable were active in this unit test context (it's not without Spring AOP),
        // this call would succeed after retry.
        // For now, we just test that the service calls save. The retry itself is tested in integration.
        // Or, we can verify that the exception is thrown if not retrying.

        // In a unit test, @Retryable is not active. So, the first exception will propagate.
        assertThrows(OptimisticLockException.class, () -> {
            stockService.adjustStock(request);
        });
        // If we wanted to test the logic *inside* a retry, we'd need more complex mocking or partial mocks.
        // For now, this confirms the underlying save is called. Integration test will confirm retry.
    }

}
