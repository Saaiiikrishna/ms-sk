package com.mysillydreams.catalogservice.service.search; // Moving to .search as it's related to reacting to events like indexer

import com.mysillydreams.catalogservice.config.CacheKeyConstants;
import com.mysillydreams.catalogservice.dto.CartDto; // For template type
import com.mysillydreams.catalogservice.kafka.event.*; // Import all event types
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class CacheInvalidationService {

    // Using StringRedisTemplate for generic key operations if values are not always CartDto
    // Or inject specific templates if types are known.
    // For CartDto, we have cartDtoRedisTemplate. For others, a generic one.
    // Assuming genericRedisTemplate is configured with StringRedisSerializer for keys, like RedisCacheManager.
    private final RedisTemplate<String, Object> genericRedisTemplate;
    // private final RedisTemplate<String, CartDto> cartDtoRedisTemplate; // Keep if used elsewhere for specific CartDto ops

    // --- Methods called by DynamicPriceUpdateListener ---

    public void evictCatalogItemCache(UUID itemId) {
        if (itemId == null) return;
        String key = CacheKeyConstants.CATALOG_ITEM_CACHE_NAME + "::" + itemId.toString();
        log.info("CacheInvalidator: Evicting catalogItem cache for key: {}", key);
        genericRedisTemplate.delete(key);
    }

    public void evictPriceDetailCache(UUID itemId) {
        if (itemId == null) return;
        // Uses the pattern defined in CacheKeyConstants that matches Spring's default key generation.
        String pricePattern = CacheKeyConstants.getPriceDetailCachePatternByItem(itemId);
        log.info("CacheInvalidator: Evicting priceDetail caches with pattern: {}", pricePattern);
        Set<String> keys = genericRedisTemplate.keys(pricePattern);
        if (keys != null && !keys.isEmpty()) {
            log.info("CacheInvalidator: Found {} priceDetail keys to evict for pattern {}", keys.size(), pricePattern);
            genericRedisTemplate.delete(keys);
        } else {
            log.info("CacheInvalidator: No priceDetail keys found for pattern: {}", pricePattern);
        }
    }


    // --- Kafka Event Listeners (Secondary Invalidation Mechanism / Broader Scopes) ---
    // These can be kept if they serve purposes beyond what DynamicPriceUpdateListener handles directly,
    // e.g., if other services might update items/prices without going through the primary listener's flow,
    // or for broader invalidations like "all items in category X".
    // For now, the direct calls from DynamicPriceUpdateListener are primary for its changes.
    // The existing Kafka listeners in this service might become redundant for price/item updates
    // if DynamicPriceUpdateListener is the sole path for such changes that require cache eviction.
    // Let's assume they might still be useful for other scenarios or as a fallback.

    @KafkaListener(
            topics = "${app.kafka.topic.item-updated}",
            groupId = "catalog-cache-invalidator-item-updated",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onItemUpdated(@Payload CatalogItemEvent event) {
        log.info("CacheInvalidator (Kafka): Received item.updated event for item ID: {}", event.getItemId());
        evictCatalogItemCache(event.getItemId()); // Evict general item cache
        evictPriceDetailCache(event.getItemId()); // Prices might have changed too
        // Consider cart invalidation if item details shown in cart change significantly
        log.warn("TODO (Kafka Invalidator): Implement targeted CartDto cache invalidation for item ID {} update, if required beyond TTL.", event.getItemId());
    }

    @KafkaListener(
            topics = "${app.kafka.topic.item-deleted}",
            groupId = "catalog-cache-invalidator-item-deleted",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onItemDeleted(@Payload CatalogItemEvent event) {
        log.info("CacheInvalidator (Kafka): Received item.deleted event for item ID: {}", event.getItemId());
        evictCatalogItemCache(event.getItemId());
        evictPriceDetailCache(event.getItemId());
        log.warn("TODO (Kafka Invalidator): Implement targeted CartDto cache invalidation for item ID {} deletion, if required beyond TTL.", event.getItemId());
    }

    @KafkaListener(
            topics = "${app.kafka.topic.price-updated}", // This is likely the event from ItemService base price changes
            groupId = "catalog-cache-invalidator-price-updated",
            containerFactory = "priceUpdatedEventKafkaListenerContainerFactory" // Ensure this factory exists and is correctly typed
    )
    public void onBasePriceUpdatedFromItemService(@Payload PriceUpdatedEvent event) { // Assuming this is com.mysillydreams.catalogservice.kafka.event.PriceUpdatedEvent
        log.info("CacheInvalidator (Kafka): Received base price.updated event for item ID: {}", event.getItemId());
        // This event might be for base price changes. DynamicPriceUpdateListener handles dynamic price changes.
        // Evicting priceDetail is crucial as it depends on basePrice if no dynamic price is set.
        evictPriceDetailCache(event.getItemId());
        // CatalogItemDto also contains basePrice, so evict it too.
        evictCatalogItemCache(event.getItemId());
        log.warn("TODO (Kafka Invalidator): Implement targeted CartDto cache invalidation for item ID {} base price update, if required beyond TTL.", event.getItemId());
    }


    @KafkaListener(
            topics = "${app.kafka.topic.bulk-rule-added}", // Or a more generic topic like "bulk-rule-changed"
            groupId = "catalog-cache-invalidator-bulk-rule",
            containerFactory = "bulkPricingRuleEventKafkaListenerContainerFactory"
    )
    public void onBulkPricingRuleChanged(@Payload BulkPricingRuleEvent event) {
        log.info("CacheInvalidator (Kafka): Received {} event for item ID: {}", event.getEventType(), event.getItemId());
        // Bulk rule changes affect price calculations.
        evictPriceDetailCache(event.getItemId()); // Evict all price details for the item.
        log.warn("TODO (Kafka Invalidator): Implement targeted CartDto cache invalidation for item ID {} bulk rule change, if required beyond TTL.", event.getItemId());
    }

    @KafkaListener(
            topics = "${app.kafka.topic.stock-changed}",
            groupId = "catalog-cache-invalidator-stock-changed",
            containerFactory = "stockLevelChangedEventKafkaListenerContainerFactory"
    )
    public void onStockLevelChanged(@Payload StockLevelChangedEvent event) {
        log.info("CacheInvalidator (Kafka): Received stock.level.changed event for item ID: {}", event.getItemId());
        // If CatalogItemDto includes stock level, evict it.
        // evictCatalogItemCache(event.getItemId()); // If CatalogItemDto.quantityOnHand is part of it and cached
        log.info("CacheInvalidator (Kafka): Stock level changed for item {}. CatalogItemDto cache might need eviction if it includes stock.", event.getItemId());
        // PriceDetailDto typically doesn't include stock, so no eviction needed for it based on stock change unless pricing rules depend on stock.
        log.warn("TODO (Kafka Invalidator): Consider CartDto cache invalidation for item ID {} stock change if cart display is affected beyond availability checks.", event.getItemId());
    }

    // Removed old helper methods like evictItemSpecificCaches, evictItemStockCache, evictItemPriceCaches
    // as they were using different key structures and are replaced by the new specific methods.
}
