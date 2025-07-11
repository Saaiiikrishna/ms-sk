package com.mysillydreams.userservice.service.inventory;

import com.mysillydreams.userservice.domain.inventory.InventoryItem;
import com.mysillydreams.userservice.domain.inventory.StockTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import java.util.concurrent.CompletableFuture;

import java.util.HashMap;
import java.util.Map;

@Service
public class InventoryKafkaClient {

    private static final Logger logger = LoggerFactory.getLogger(InventoryKafkaClient.class);

    private final KafkaTemplate<String, Object> kafkaTemplate; // Renamed from 'tpl' for clarity

    private final String itemCreatedTopic;
    private final String stockAdjustedTopic;

    @Autowired
    public InventoryKafkaClient(KafkaTemplate<String, Object> kafkaTemplate,
                                @Value("${inventory.topic.itemCreated:inventory.item.created.v1}") String itemCreatedTopic,
                                @Value("${inventory.topic.stockAdjusted:inventory.stock.adjusted.v1}") String stockAdjustedTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.itemCreatedTopic = itemCreatedTopic;
        this.stockAdjustedTopic = stockAdjustedTopic;
    }

    /**
     * Publishes an event when a new inventory item is created.
     * Uses item ID as the Kafka message key.
     * @param item The created InventoryItem.
     */
    public void publishItemCreated(InventoryItem item) {
        if (item == null || item.getId() == null) {
            logger.warn("Attempted to publish item created event for null item or item with null ID. Skipping.");
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("itemId", item.getId().toString());
        payload.put("sku", item.getSku());
        payload.put("name", item.getName()); // Consider if name is PII and should be omitted/masked
        if (item.getOwner() != null && item.getOwner().getId() != null) {
            payload.put("inventoryProfileId", item.getOwner().getId().toString());
        }
        payload.put("quantityOnHand", item.getQuantityOnHand());
        payload.put("reorderLevel", item.getReorderLevel());
        payload.put("createdAt", item.getCreatedAt().toString());
        payload.put("eventType", "InventoryItemCreated");


        logger.info("Publishing InventoryItemCreated event for ItemId: {}, SKU: {}, Topic: {}",
                item.getId(), item.getSku(), itemCreatedTopic);

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(itemCreatedTopic, item.getId().toString(), payload);
        addKafkaCallback(future, "InventoryItemCreated", item.getId().toString());
    }

    /**
     * Publishes an event when an inventory item's stock is adjusted.
     * Uses item ID as the Kafka message key.
     * @param item The InventoryItem whose stock was adjusted.
     * @param transaction The StockTransaction record for this adjustment.
     */
    public void publishStockAdjusted(InventoryItem item, StockTransaction transaction) {
        if (item == null || item.getId() == null || transaction == null || transaction.getId() == null) {
            logger.warn("Attempted to publish stock adjusted event with null item/transaction or null IDs. Skipping.");
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("itemId", item.getId().toString());
        payload.put("sku", item.getSku());
        payload.put("transactionId", transaction.getId().toString());
        payload.put("transactionType", transaction.getType().toString());
        payload.put("quantityAdjusted", transaction.getQuantity()); // The amount of change
        payload.put("newQuantityOnHand", item.getQuantityOnHand()); // Quantity after adjustment
        payload.put("transactionTimestamp", transaction.getTimestamp().toString());
        if (item.getOwner() != null && item.getOwner().getId() != null) {
            payload.put("inventoryProfileId", item.getOwner().getId().toString());
        }
        payload.put("eventType", "InventoryStockAdjusted");

        logger.info("Publishing InventoryStockAdjusted event for ItemId: {}, SKU: {}, TransactionType: {}, Topic: {}",
                item.getId(), item.getSku(), transaction.getType(), stockAdjustedTopic);

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(stockAdjustedTopic, item.getId().toString(), payload);
        addKafkaCallback(future, "InventoryStockAdjusted", item.getId().toString());
    }

    private void addKafkaCallback(CompletableFuture<SendResult<String, Object>> future, String eventType, String recordKey) {
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                logger.info("Successfully published '{}' event for Key: {}. Topic: {}, Partition: {}, Offset: {}",
                        eventType,
                        recordKey,
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                logger.error("Failed to publish '{}' event for Key: {}. Error: {}",
                        eventType, recordKey, ex.getMessage(), ex);
                 // In a real app, consider retry, DLQ, or more robust error handling.
            }
        });
    }
}
