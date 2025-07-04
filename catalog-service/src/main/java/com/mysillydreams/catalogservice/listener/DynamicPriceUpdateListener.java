package com.mysillydreams.catalogservice.listener;

import com.mysillydreams.catalogservice.domain.model.CatalogItemEntity;
import com.mysillydreams.catalogservice.domain.repository.CatalogItemRepository;
import com.mysillydreams.catalogservice.dto.PriceUpdatedEventDto;
import com.mysillydreams.catalogservice.service.search.CacheInvalidationService;
import com.mysillydreams.catalogservice.service.search.CatalogItemIndexerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicPriceUpdateListener {

    private final CatalogItemRepository catalogItemRepository;
    private final CacheInvalidationService cacheInvalidationService; // For Redis eviction
    private final CatalogItemIndexerService catalogItemIndexerService; // For OpenSearch re-indexing

    @KafkaListener(
            topics = "${app.kafka.topic.price-updated-from-engine}", // Matches application.yml key
            groupId = "catalog-service-price-update-listener", // Specific group ID for this listener
            containerFactory = "kafkaListenerContainerFactory" // Assuming default factory is fine for PriceUpdatedEventDto
    )
    @Transactional // Process each message in a new transaction
    public void onPriceUpdated(@Payload PriceUpdatedEventDto event) {
        log.info("Received PriceUpdatedEvent for itemId: {}, new finalPrice: {}", event.getItemId(), event.getFinalPrice());

        if (event.getItemId() == null || event.getFinalPrice() == null) {
            log.error("Received PriceUpdatedEvent with null itemId or finalPrice: {}", event);
            // Consider sending to a DLT if this is a common or critical issue
            return;
        }

        try {
            Optional<CatalogItemEntity> itemOptional = catalogItemRepository.findById(event.getItemId());

            if (itemOptional.isPresent()) {
                CatalogItemEntity item = itemOptional.get();

                // Log previous price for auditing/comparison if needed
                log.debug("Updating dynamicPrice for item {}. Old dynamicPrice: {}, New finalPrice from event: {}",
                         item.getId(), item.getDynamicPrice(), event.getFinalPrice());

                item.setDynamicPrice(event.getFinalPrice());
                // Potentially store event.getBasePrice() or event.getComponents() if CatalogItemEntity is extended further
                // For now, just updating the dynamicPrice field.

                catalogItemRepository.save(item);
                log.info("Successfully updated dynamicPrice for item ID: {}", item.getId());

                // Evict cache
                try {
                    cacheInvalidationService.evictItemPriceCaches(item.getId().toString());
                    log.info("Successfully requested cache eviction for item ID: {}", item.getId());
                } catch (Exception e) {
                    log.error("Error requesting cache eviction for item ID {}: {}", item.getId(), e.getMessage(), e);
                    // Non-fatal, continue to re-indexing
                }

                // Trigger re-indexing
                try {
                    catalogItemIndexerService.reindexItem(item.getId());
                    log.info("Successfully requested re-indexing for item ID: {}", item.getId());
                } catch (Exception e) {
                    log.error("Error requesting re-indexing for item ID {}: {}", item.getId(), e.getMessage(), e);
                    // Non-fatal
                }

            } else {
                log.warn("Item not found for ID {} from PriceUpdatedEvent. Cannot update dynamic price.", event.getItemId());
                // Consider if this scenario requires alerting or DLT
            }
        } catch (Exception e) {
            log.error("Generic error processing PriceUpdatedEvent for itemId {}: {}", event.getItemId(), e.getMessage(), e);
            // Consider sending to a DLT
            // Re-throw if the transaction should roll back and message be retried by Kafka (depends on error handler config)
            // throw e;
        }
    }
}
