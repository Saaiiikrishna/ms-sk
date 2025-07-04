package com.mysillydreams.catalogservice.config;

public class CacheKeyConstants {

    // Active Cart DTO, keyed by userId
    public static final String ACTIVE_CART_DTO_PREFIX = "cartDto:active:user::"; // e.g., cartDto:active:user::user123

    // Potential future caches (not yet implemented, but for invalidator design)
    public static final String ITEM_STOCK_LEVEL_PREFIX = "itemCache:stock::";      // e.g., itemCache:stock::itemUUID
    public static final String ITEM_PRICE_DETAIL_PREFIX = "itemCache:priceDetail::"; // e.g., itemCache:priceDetail::itemUUID::qty:5
                                                                                // (key would include quantity for price details)

    public static String getActiveCartDtoKey(String userId) {
        return ACTIVE_CART_DTO_PREFIX + userId;
    }

    public static String getItemStockLevelKey(String itemId) {
        return ITEM_STOCK_LEVEL_PREFIX + itemId;
    }

    public static String getItemPriceDetailKey(String itemId, int quantity) {
        return ITEM_PRICE_DETAIL_PREFIX + itemId + "::qty:" + quantity;
    }

    // Pattern for deleting all price details for an item if quantity is part of key
    public static String getItemPriceDetailPattern(String itemId) {
        return ITEM_PRICE_DETAIL_PREFIX + itemId + "::qty:*";
    }
}
