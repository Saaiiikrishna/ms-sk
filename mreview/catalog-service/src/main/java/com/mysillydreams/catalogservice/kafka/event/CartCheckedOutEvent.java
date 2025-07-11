package com.mysillydreams.catalogservice.kafka.event;

import com.mysillydreams.catalogservice.dto.CartItemDetailDto; // Reusing for line item details
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartCheckedOutEvent {
    private UUID eventId;
    private UUID cartId;
    private String userId;
    private List<CartCheckedOutItem> items; // Detailed line items
    private BigDecimal subtotal;
    private BigDecimal totalDiscountAmount;
    private BigDecimal finalTotal;
    private Instant checkoutTimestamp;

    // Inner class for item details in the event
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CartCheckedOutItem {
        private UUID catalogItemId;
        private String sku;
        private String name;
        private int quantity;
        private BigDecimal originalUnitPrice;
        private BigDecimal discountAppliedPerUnit;
        private BigDecimal finalUnitPrice;
        private BigDecimal lineItemTotal;
    }
}
