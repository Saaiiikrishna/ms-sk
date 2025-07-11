package com.mysillydreams.catalogservice.config;

public class CacheKeyConstants {

    // Cache Names (used by Spring @Cacheable)
    public static final String CATALOG_ITEM_CACHE_NAME = "catalogItem";
    public static final String PRICE_DETAIL_CACHE_NAME = "priceDetail";
    public static final String ACTIVE_CART_CACHE_NAME = "activeCarts"; // Already used in RedisConfig for TTL

    // Key Prefixes/Structure (mostly for manual Redis ops or complex key generation)
    // Spring Cache typically handles prefixing with cache name automatically.
    // These are more for services like CacheInvalidationService if it needs to build keys/patterns.

    // Active Cart DTO, keyed by userId (if direct Redis ops)
    // For @Cacheable("activeCarts", key="#userId"), Spring generates "activeCarts::userIdValue"
    public static final String ACTIVE_CART_KEY_PREFIX_MANUAL = "cartDto:active:user::";


    // For CacheInvalidationService or direct Redis ops for priceDetail, if needed.
    // Note: Spring Cache keys for priceDetail would be like "priceDetail::itemId_qty_value"
    // The pattern needs to match how Spring stores them OR CacheInvalidationService needs to know the exact cache name.
    // Let's assume CacheInvalidationService will use the cache name directly.

    public static String getActiveCartDtoManualKey(String userId) {
        return ACTIVE_CART_KEY_PREFIX_MANUAL + userId;
    }

    // Key for @Cacheable(cacheNames = PRICE_DETAIL_CACHE_NAME, keyGenerator = "priceDetailKeyGenerator")
    // or key = "#itemId.toString() + '::qty:' + #quantity"
    // The generated key by Spring would be like: "priceDetail::uuid_as_string::qty:5"
    // So the pattern for invalidation should be "priceDetail::uuid_as_string::qty:*"

    public static String getPriceDetailCacheKey(UUID itemId, int quantity) {
        // This helper generates the part *after* the cache name prefix.
        return itemId.toString() + "::qty:" + quantity;
    }

    public static String getPriceDetailCachePatternByItem(UUID itemId) {
        // This pattern assumes Spring's default key generation using '::' after cache name.
        return PRICE_DETAIL_CACHE_NAME + "::" + itemId.toString() + "::qty:*";
    }

    // Key for @Cacheable(cacheNames = CATALOG_ITEM_CACHE_NAME, key = "#itemId.toString()")
    // Spring generated key: "catalogItem::uuid_as_string"
    public static String getCatalogItemCacheKey(UUID itemId) {
        // This helper generates the part *after* the cache name prefix.
        return itemId.toString();
    }
}
