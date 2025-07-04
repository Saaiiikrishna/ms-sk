package com.mysillydreams.catalogservice.service.pricing;

import com.mysillydreams.catalogservice.dto.PricingComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class StubDynamicPricingEngine implements DynamicPricingEngine {

    @Override
    public List<PricingComponent> evaluate(UUID itemId, int quantity, BigDecimal currentPrice) {
        log.debug("StubDynamicPricingEngine called for itemId: {}, quantity: {}, currentPrice: {}. Returning no dynamic adjustments.",
                itemId, quantity, currentPrice);

        // In a real implementation, this would connect to a rules engine, ML model,
        // Kafka Streams app, or other system to determine dynamic price adjustments.

        // Example of a possible dynamic component (commented out):
        /*
        if (itemId.toString().endsWith("1")) { // Arbitrary condition
            return List.of(
                PricingComponent.builder()
                    .code("FLASH_SALE_10_PERCENT")
                    .description("Flash Sale: 10% off")
                    .amount(currentPrice.multiply(new BigDecimal("-0.10")).setScale(2, RoundingMode.HALF_UP))
                    .build()
            );
        }
        */

        return Collections.emptyList();
    }
}
