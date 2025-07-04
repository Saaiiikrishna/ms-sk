package com.mysillydreams.catalogservice.listener;

import com.mysillydreams.catalogservice.domain.model.CatalogItemEntity;
import com.mysillydreams.catalogservice.domain.repository.CatalogItemRepository;
import com.mysillydreams.catalogservice.dto.PriceUpdatedEventDto;
import com.mysillydreams.catalogservice.service.search.CacheInvalidationService;
import com.mysillydreams.catalogservice.service.search.CatalogItemIndexerService;
import com.fasterxml.jackson.core.JsonProcessingException; // For DTO to JSON for DLT
import com.fasterxml.jackson.databind.ObjectMapper; // For DTO to JSON for DLT
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value; // For DLT topic name
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate; // For DLT
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicPriceUpdateListener {

    private final CatalogItemRepository catalogItemRepository;
    private final CacheInvalidationService cacheInvalidationService;
    private final CatalogItemIndexerService catalogItemIndexerService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topic.price-update-listener-dlt}")
    private String dltTopic;

    @KafkaListener(
            topics = "${app.kafka.topic.price-updated-from-engine}",
            groupId = "catalog-service-price-update-listener",
            containerFactory = "stringPayloadKafkaListenerContainerFactory" // Use the new factory
    )
    @Transactional
    public void onPriceUpdated(@Payload String rawPayload, @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        log.info("Received raw PriceUpdatedEvent payload. Key: {}", key);
        PriceUpdatedEventDto event = null;
        try {
            event = objectMapper.readValue(rawPayload, PriceUpdatedEventDto.class);
            log.info("Successfully deserialized PriceUpdatedEvent for itemId: {}, new finalPrice: {}", event.getItemId(), event.getFinalPrice());

            if (event.getItemId() == null || event.getFinalPrice() == null) {
                log.error("Deserialized PriceUpdatedEvent with null itemId or finalPrice: {}. Sending raw payload to DLT.", event);
                String dltKey = (key != null) ? key : (event.getItemId() != null ? event.getItemId().toString() : "unknown_item_payload_issue_" + System.currentTimeMillis());
                sendToDlt(dltKey, rawPayload, "NullItemIdOrFinalPriceInPayload", null);
                return;
            }

            // Business logic starts here
            // Idempotency Check
            if (event.getTimestamp() == null) {
                // This case should ideally not happen if pricing-engine always sets it.
                // Depending on strictness, could send to DLT or proceed with a warning.
                // For now, log a warning and proceed. Stricter handling might send to DLT.
                log.warn("PriceUpdatedEvent for itemId: {} received without a timestamp. Proceeding without idempotency check based on event timestamp.", event.getItemId());
            }

            Optional<CatalogItemEntity> itemOptional = catalogItemRepository.findById(event.getItemId());

            if (itemOptional.isPresent()) {
                CatalogItemEntity item = itemOptional.get();

                // Idempotency check using event timestamp
                if (event.getTimestamp() != null && item.getDynamicPriceLastAppliedTimestamp() != null &&
                    !event.getTimestamp().isAfter(item.getDynamicPriceLastAppliedTimestamp())) {
                    log.info("Skipping PriceUpdatedEvent for itemId: {} due to idempotency. Event timestamp ({}) is not newer than last applied timestamp ({}).",
                            event.getItemId(), event.getTimestamp(), item.getDynamicPriceLastAppliedTimestamp());
                    return; // Already processed a newer or same-timestamp event
                }

                item.setDynamicPrice(event.getFinalPrice());
                item.setLastModifiedDate(Instant.now()); // General entity update timestamp
                item.setDynamicPriceLastAppliedTimestamp(event.getTimestamp()); // Set specific timestamp for this price update
                catalogItemRepository.save(item);
                log.info("Successfully updated dynamicPrice for itemId: {} to {}. Applied eventTimestamp: {}", event.getItemId(), event.getFinalPrice(), event.getTimestamp());

                cacheInvalidationService.evictCatalogItemCache(item.getId());
                cacheInvalidationService.evictPriceDetailCache(item.getId());
                catalogItemIndexerService.reindexItem(item.getId());

            } else {
                log.warn("CatalogItemEntity not found for itemId: {}. Price update cannot be applied. Sending raw payload to DLT.", event.getItemId());
                String dltKey = (key != null) ? key : event.getItemId().toString();
                sendToDlt(dltKey, rawPayload, "CatalogItemNotFoundAfterDeserialization", null);
            }
        // Catch block for deserialization errors (JsonProcessingException is a good specific one)
        } catch (JsonProcessingException jpe) {
            log.error("Error deserializing PriceUpdatedEvent from raw payload. Sending raw payload to DLT. Key: {}. Error: {}", key, jpe.getMessage(), jpe);
            String dltKey = (key != null) ? key : "unknown_key_deserialization_error_" + System.currentTimeMillis();
            sendToDlt(dltKey, rawPayload, "JsonDeserializationError", jpe);
            // Re-throw to ensure transaction rollback and potential listener retry if configured for such errors.
            // Or, if retrying deserialization errors is pointless, don't re-throw or throw a non-retryable exception.
            // For now, let's re-throw as a generic runtime to ensure transaction rollback by default.
            throw new RuntimeException("Failed to deserialize Kafka message: " + jpe.getMessage(), jpe);
        } catch (OptimisticLockingFailureException e) {
            // Event is not null here if deserialization succeeded
            String itemIdStr = (event != null && event.getItemId() != null) ? event.getItemId().toString() : "unknown_item";
            log.warn("Optimistic lock failure for itemId: {}. This might indicate concurrent updates. Sending raw payload to DLT. Error: {}", itemIdStr, e.getMessage());
            String dltKey = (key != null) ? key : (itemIdStr.equals("unknown_item") ? "unknown_item_opt_lock_" + System.currentTimeMillis() : itemIdStr);
            sendToDlt(dltKey, rawPayload, "OptimisticLockingFailure", e);
            throw e; // Re-throw to ensure transaction rollback and allow Kafka listener retries
        } catch (Exception e) {
            // Event might be null if error occurred before or during deserialization (though JsonProcessingException should catch most deserialization issues)
            String itemIdStr = (event != null && event.getItemId() != null) ? event.getItemId().toString() : "unknown_item";
            log.error("Error processing PriceUpdatedEvent for itemId: {}. Sending raw payload to DLT. Error: {}", itemIdStr, e.getMessage(), e);
            String dltKey = (key != null) ? key : (itemIdStr.equals("unknown_item") ? "unknown_item_processing_exception_" + System.currentTimeMillis() : itemIdStr);
            sendToDlt(dltKey, rawPayload, e.getClass().getSimpleName(), e);
            throw new RuntimeException("Error processing PriceUpdatedEvent (raw payload sent to DLT): " + e.getMessage(), e);
        }
    }

    @Value("${app.kafka.topic.price-updated-from-engine}")
    private String sourceTopic; // Inject the source topic name

    private void sendToDlt(String key, String rawPayload, String errorType, @Nullable Exception exception) {
        try {
            log.info("Sending message with key '{}' to DLT topic '{}' due to error: {}", key, dltTopic, errorType);
            ProducerRecord<String, String> record = new ProducerRecord<>(dltTopic, key, rawPayload);
            record.headers().add("x-original-topic", Bytes.wrap(sourceTopic.getBytes(StandardCharsets.UTF_8))); // Use injected source topic
            record.headers().add("x-error-type", Bytes.wrap(errorType.getBytes(StandardCharsets.UTF_8)));
            if (exception != null) {
                record.headers().add("x-exception-message", Bytes.wrap(exception.getMessage().getBytes(StandardCharsets.UTF_8)));
                record.headers().add("x-exception-stacktrace", Bytes.wrap(getStackTraceAsString(exception).getBytes(StandardCharsets.UTF_8)));
            }
            // kafkaTemplate.send(record).get(); // .get() makes it synchronous, consider async with callback for production
            kafkaTemplate.send(record).whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Successfully sent to DLT: key='{}', topic='{}'", key, dltTopic);
                } else {
                    log.error("Failed to send to DLT: key='{}', topic='{}'. Error: {}", key, dltTopic, ex.getMessage(), ex);
                }
            });
        } catch (Exception e) {
            log.error("Critical error: Failed to send message with key '{}' to DLT topic '{}'. Error: {}", key, dltTopic, e.getMessage(), e);
            // This is a fallback log, as sending to DLT itself failed.
        }
    }

    private String getStackTraceAsString(Throwable throwable) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        throwable.printStackTrace(printWriter);
        return stringWriter.toString();
    }
}

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
