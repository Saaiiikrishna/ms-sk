package com.mysillydreams.userservice.service.inventory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.userservice.domain.inventory.InventoryItem;
import com.mysillydreams.userservice.domain.inventory.InventoryProfile;
import com.mysillydreams.userservice.domain.inventory.StockTransaction;
import com.mysillydreams.userservice.domain.inventory.TransactionType;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;


import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EmbeddedKafka(partitions = 1,
               topics = {
                    "${inventory.topic.itemCreated:inventory.item.created.v1}",
                    "${inventory.topic.stockAdjusted:inventory.stock.adjusted.v1}"
               },
               brokerProperties = {"listeners=PLAINTEXT://localhost:9100", "port=9100"}) // Yet another unique port
public class InventoryKafkaClientIntegrationTest {

    @Autowired
    private InventoryKafkaClient inventoryKafkaClient;

    @Value("${inventory.topic.itemCreated}")
    private String itemCreatedTopic;
    @Value("${inventory.topic.stockAdjusted}")
    private String stockAdjustedTopic;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory; // Autowired from Spring Boot test config

    @Autowired
    private ObjectMapper objectMapper; // Autowired from Spring Boot

    private KafkaConsumer<String, String> consumer;

    @BeforeEach
    void setUpKafkaConsumer() {
        // Setup a Kafka consumer for the test topics
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("inventory-kafka-test-group", "true", EmbeddedKafkaBroker.BROKER_ADDRESS_PLACEHOLDER);
        consumerProps.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer"); // Consume raw JSON string
        consumer = (KafkaConsumer<String, String>) consumerFactory.createConsumer("inventory-kafka-test-group", null, null, consumerProps);
    }

    @AfterEach
    void tearDownKafkaConsumer() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    void publishItemCreated_sendsCorrectEventToItemTopic() throws Exception {
        consumer.subscribe(Collections.singletonList(itemCreatedTopic));

        InventoryProfile owner = new InventoryProfile(UUID.randomUUID());
        InventoryItem item = new InventoryItem();
        item.setId(UUID.randomUUID());
        item.setSku("SKU-KAFKA-001");
        item.setName("Kafka Test Item");
        item.setOwner(owner);
        item.setQuantityOnHand(10);
        item.setReorderLevel(2);
        item.setCreatedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS)); // Truncate for consistent string comparison

        inventoryKafkaClient.publishItemCreated(item);

        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10).toMillis(), 1);
        assertThat(records.count()).isEqualTo(1);
        ConsumerRecord<String, String> record = records.iterator().next();

        assertThat(record.key()).isEqualTo(item.getId().toString());
        Map<String, Object> payload = objectMapper.readValue(record.value(), new TypeReference<>() {});

        assertThat(payload.get("itemId")).isEqualTo(item.getId().toString());
        assertThat(payload.get("sku")).isEqualTo(item.getSku());
        assertThat(payload.get("name")).isEqualTo(item.getName());
        assertThat(payload.get("inventoryProfileId")).isEqualTo(owner.getId().toString());
        assertThat(payload.get("quantityOnHand")).isEqualTo(item.getQuantityOnHand());
        assertThat(payload.get("reorderLevel")).isEqualTo(item.getReorderLevel());
        assertThat(payload.get("createdAt")).isEqualTo(item.getCreatedAt().toString());
        assertThat(payload.get("eventType")).isEqualTo("InventoryItemCreated");
    }

    @Test
    void publishStockAdjusted_sendsCorrectEventToStockTopic() throws Exception {
        consumer.subscribe(Collections.singletonList(stockAdjustedTopic));

        InventoryProfile owner = new InventoryProfile(UUID.randomUUID());
        InventoryItem item = new InventoryItem();
        item.setId(UUID.randomUUID());
        item.setSku("SKU-KAFKA-002");
        item.setName("Kafka Stock Adjust Item");
        item.setOwner(owner);
        item.setQuantityOnHand(100); // Quantity *after* adjustment
        item.setReorderLevel(20);
        item.setCreatedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));

        StockTransaction transaction = new StockTransaction();
        transaction.setId(UUID.randomUUID());
        transaction.setItem(item);
        transaction.setType(TransactionType.ADJUSTMENT);
        transaction.setQuantity(5); // The amount of change
        transaction.setTimestamp(Instant.now().truncatedTo(ChronoUnit.MILLIS));

        inventoryKafkaClient.publishStockAdjusted(item, transaction);

        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10).toMillis(), 1);
        assertThat(records.count()).isEqualTo(1);
        ConsumerRecord<String, String> record = records.iterator().next();

        assertThat(record.key()).isEqualTo(item.getId().toString());
        Map<String, Object> payload = objectMapper.readValue(record.value(), new TypeReference<>() {});

        assertThat(payload.get("itemId")).isEqualTo(item.getId().toString());
        assertThat(payload.get("sku")).isEqualTo(item.getSku());
        assertThat(payload.get("transactionId")).isEqualTo(transaction.getId().toString());
        assertThat(payload.get("transactionType")).isEqualTo(transaction.getType().toString());
        assertThat(payload.get("quantityAdjusted")).isEqualTo(transaction.getQuantity());
        assertThat(payload.get("newQuantityOnHand")).isEqualTo(item.getQuantityOnHand());
        assertThat(payload.get("transactionTimestamp")).isEqualTo(transaction.getTimestamp().toString());
        assertThat(payload.get("inventoryProfileId")).isEqualTo(owner.getId().toString());
        assertThat(payload.get("eventType")).isEqualTo("InventoryStockAdjusted");
    }
}
