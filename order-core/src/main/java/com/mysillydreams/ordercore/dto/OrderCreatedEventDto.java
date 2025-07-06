package com.mysillydreams.ordercore.dto;

import com.mysillydreams.ordercore.domain.enums.OrderType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEventDto {
    private UUID orderId;
    private UUID customerId;
    private OrderType orderType;
    private List<LineItemDto> items; // Reusing a common LineItemDto structure
    private BigDecimal totalAmount;
    private String currency;
    private Instant createdAt;

    // Simplified LineItemDto for this event, or reference a common one
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineItemDto {
        private UUID productId;
        private String productSku;
        private int quantity;
        private BigDecimal unitPrice;
        private BigDecimal discount; // Assuming this is total discount for the line item
        private BigDecimal totalPrice;
    }
}
