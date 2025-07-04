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
    private final RedisTemplate<String, Object> genericRedisTemplate; // For item-specific caches if any
    private final RedisTemplate<String, CartDto> cartDtoRedisTemplate; // Specifically for CartDto

    // --- Item Event Listeners ---

    @KafkaListener(
            topics = "${app.kafka.topic.item-updated}",
            groupId = "catalog-cache-invalidator-item-updated",
            containerFactory = "kafkaListenerContainerFactory" // Default factory for CatalogItemEvent
    )
    public void onItemUpdated(@Payload CatalogItemEvent event) {
        log.info("CacheInvalidator: Received item.updated event for item ID: {}", event.getItemId());
        evictItemSpecificCaches(event.getItemId().toString());

        // Complex: Invalidate carts containing this item.
        // This requires an auxiliary index (itemId -> Set<userId>) or iterating all cart keys (not scalable).
        // For now, relying on CartDto TTL or next cart mutation by user to refresh.
        log.warn("TODO: Implement targeted CartDto cache invalidation for item ID {} update, if required beyond TTL.", event.getItemId());
    }

    @KafkaListener(
            topics = "${app.kafka.topic.item-deleted}",
            groupId = "catalog-cache-invalidator-item-deleted",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onItemDeleted(@Payload CatalogItemEvent event) {
        log.info("CacheInvalidator: Received item.deleted event for item ID: {}", event.getItemId());
        evictItemSpecificCaches(event.getItemId().toString());
        // Similar TODO for cart invalidation as onItemUpdated.
        log.warn("TODO: Implement targeted CartDto cache invalidation for item ID {} deletion, if required beyond TTL.", event.getItemId());
    }

    // --- Price Event Listeners ---

    @KafkaListener(
            topics = "${app.kafka.topic.price-updated}",
            groupId = "catalog-cache-invalidator-price-updated",
            // Assuming PriceUpdatedEvent needs its own factory or a generic one
            // For now, let's create a new factory for it in KafkaConsumerConfig if this doesn't work.
            // Let's assume a factory named 'priceUpdatedEventKafkaListenerContainerFactory' will be created.
            containerFactory = "priceUpdatedEventKafkaListenerContainerFactory"
    )
    public void onPriceUpdated(@Payload PriceUpdatedEvent event) {
        log.info("CacheInvalidator: Received price.updated event for item ID: {}", event.getItemId());
        evictItemPriceCaches(event.getItemId().toString());
        log.warn("TODO: Implement targeted CartDto cache invalidation for item ID {} price update, if required beyond TTL.", event.getItemId());
    }

    // --- Bulk Pricing Rule Event Listeners ---
    @KafkaListener(
            topics = "${app.kafka.topic.bulk-rule-added}", // Assuming this topic name is correct from application.yml
            groupId = "catalog-cache-invalidator-bulk-rule",
            // Assuming BulkPricingRuleEvent needs its own factory or a generic one
            containerFactory = "bulkPricingRuleEventKafkaListenerContainerFactory"
    )
    public void onBulkPricingRuleChanged(@Payload BulkPricingRuleEvent event) { // Catches added, updated, deleted
        log.info("CacheInvalidator: Received {} event for item ID: {}", event.getEventType(), event.getItemId());
        evictItemPriceCaches(event.getItemId().toString());
        log.warn("TODO: Implement targeted CartDto cache invalidation for item ID {} bulk rule change, if required beyond TTL.", event.getItemId());
    }

    // --- Stock Event Listener ---
    @KafkaListener(
            topics = "${app.kafka.topic.stock-changed}",
            groupId = "catalog-cache-invalidator-stock-changed",
            containerFactory = "stockLevelChangedEventKafkaListenerContainerFactory"
    )
    public void onStockLevelChanged(@Payload StockLevelChangedEvent event) {
        log.info("CacheInvalidator: Received stock.level.changed event for item ID: {}", event.getItemId());
        evictItemStockCache(event.getItemId().toString());
        // If cart display or add-to-cart logic depends directly on real-time stock (beyond reservation checks),
        // then carts containing this item might need invalidation.
        // Current CartDto does not include stock directly, but availability checks happen.
        log.warn("TODO: Consider CartDto cache invalidation for item ID {} stock change if cart display is affected beyond availability checks.", event.getItemId());
    }


    // --- Helper methods for eviction ---

    private void evictItemSpecificCaches(String itemId) {
        evictItemStockCache(itemId);
        evictItemPriceCaches(itemId);
    }

    private void evictItemStockCache(String itemId) {
        String stockKey = CacheKeyConstants.getItemStockLevelKey(itemId);
        // genericRedisTemplate.delete(stockKey); // If this cache existed
        log.info("CacheInvalidator: (Simulated) Evicted item stock cache for key: {}", stockKey);
    }

    private void evictItemPriceCaches(String itemId) {
        // If price details are cached per quantity, need to evict by pattern
        String pricePattern = CacheKeyConstants.getItemPriceDetailPattern(itemId);
        Set<String> keys = genericRedisTemplate.keys(pricePattern);
        if (keys != null && !keys.isEmpty()) {
            // genericRedisTemplate.delete(keys);
            log.info("CacheInvalidator: (Simulated) Evicted item price detail caches for pattern: {} ({} keys)", pricePattern, keys.size());
        } else {
            log.info("CacheInvalidator: (Simulated) No item price detail caches found for pattern: {}", pricePattern);
        }
        // Also if there's a general item base price/info cache
        // genericRedisTemplate.delete("itemCache:base::" + itemId);
    }

    // Note: Direct CartDto invalidation based on item events is complex.
    // The current strategy relies on CartService mutations updating the cache for THAT user,
    // and CartDto TTL for eventual consistency for other users' cached carts if an underlying item changes globally.
    // If more aggressive cart invalidation is needed, an auxiliary index (item_id -> set_of_user_ids_with_item_in_cart)
    // would be required, which is a larger change.
}
