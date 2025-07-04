package com.mysillydreams.catalogservice.listener;

import com.mysillydreams.catalogservice.domain.model.CatalogItemEntity;
import com.mysillydreams.catalogservice.domain.repository.CatalogItemRepository;
import com.mysillydreams.catalogservice.dto.PriceUpdatedEventDto;
import com.mysillydreams.catalogservice.dto.PricingComponentDto;
import com.mysillydreams.catalogservice.service.search.CacheInvalidationService;
import com.mysillydreams.catalogservice.service.search.CatalogItemIndexerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class DynamicPriceUpdateListenerTest {

    @Mock
    private CatalogItemRepository catalogItemRepository;
    @Mock
    private CacheInvalidationService cacheInvalidationService;
    @Mock
    private CatalogItemIndexerService catalogItemIndexerService;

    @InjectMocks
    private DynamicPriceUpdateListener listener;

    private PriceUpdatedEventDto priceUpdatedEventDto;
    private CatalogItemEntity catalogItemEntity;
    private UUID itemId;

    @BeforeEach
    void setUp() {
        itemId = UUID.randomUUID();
        priceUpdatedEventDto = PriceUpdatedEventDto.builder()
                .eventId(UUID.randomUUID())
                .itemId(itemId)
                .basePrice(new BigDecimal("100.00"))
                .finalPrice(new BigDecimal("90.00"))
                .currency("USD")
                .timestamp(Instant.now())
                .components(Collections.singletonList(PricingComponentDto.builder().componentName("SALE").value(new BigDecimal("-10.00")).build()))
                .build();

        catalogItemEntity = new CatalogItemEntity();
        catalogItemEntity.setId(itemId);
        catalogItemEntity.setName("Test Item");
        catalogItemEntity.setBasePrice(new BigDecimal("100.00"));
    }

    @Test
    void onPriceUpdated_whenItemExists_updatesPriceAndTriggersDownstream() {
        when(catalogItemRepository.findById(itemId)).thenReturn(Optional.of(catalogItemEntity));
        when(catalogItemRepository.save(any(CatalogItemEntity.class))).thenReturn(catalogItemEntity);

        listener.onPriceUpdated(priceUpdatedEventDto);

        verify(catalogItemRepository).findById(itemId);
        ArgumentCaptor<CatalogItemEntity> itemCaptor = ArgumentCaptor.forClass(CatalogItemEntity.class);
        verify(catalogItemRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getDynamicPrice()).isEqualByComparingTo("90.00");

        verify(cacheInvalidationService).evictItemPriceCaches(itemId.toString());
        verify(catalogItemIndexerService).reindexItem(itemId);
    }

    @Test
    void onPriceUpdated_whenItemNotFound_logsWarningAndDoesNotProceed() {
        when(catalogItemRepository.findById(itemId)).thenReturn(Optional.empty());

        listener.onPriceUpdated(priceUpdatedEventDto);

        verify(catalogItemRepository).findById(itemId);
        verify(catalogItemRepository, never()).save(any());
        verify(cacheInvalidationService, never()).evictItemPriceCaches(anyString());
        verify(catalogItemIndexerService, never()).reindexItem(any(UUID.class));
    }

    @Test
    void onPriceUpdated_whenEventHasNullItemId_logsErrorAndReturns() {
        priceUpdatedEventDto.setItemId(null);
        listener.onPriceUpdated(priceUpdatedEventDto);
        verifyNoInteractions(catalogItemRepository, cacheInvalidationService, catalogItemIndexerService);
    }

    @Test
    void onPriceUpdated_whenEventHasNullFinalPrice_logsErrorAndReturns() {
        priceUpdatedEventDto.setFinalPrice(null);
        listener.onPriceUpdated(priceUpdatedEventDto);
        verifyNoInteractions(catalogItemRepository, cacheInvalidationService, catalogItemIndexerService);
    }

    @Test
    void onPriceUpdated_cacheEvictionFails_stillAttemptsReindex() {
        when(catalogItemRepository.findById(itemId)).thenReturn(Optional.of(catalogItemEntity));
        doThrow(new RuntimeException("Cache eviction failed")).when(cacheInvalidationService).evictItemPriceCaches(itemId.toString());

        listener.onPriceUpdated(priceUpdatedEventDto);

        verify(catalogItemRepository).save(any(CatalogItemEntity.class));
        verify(cacheInvalidationService).evictItemPriceCaches(itemId.toString());
        verify(catalogItemIndexerService).reindexItem(itemId); // Should still be called
    }

    @Test
    void onPriceUpdated_reindexFails_completesProcessing() {
        when(catalogItemRepository.findById(itemId)).thenReturn(Optional.of(catalogItemEntity));
        doThrow(new RuntimeException("Reindex failed")).when(catalogItemIndexerService).reindexItem(itemId);

        listener.onPriceUpdated(priceUpdatedEventDto);

        verify(catalogItemRepository).save(any(CatalogItemEntity.class));
        verify(cacheInvalidationService).evictItemPriceCaches(itemId.toString());
        verify(catalogItemIndexerService).reindexItem(itemId); // Called, but exception is caught
    }
}
