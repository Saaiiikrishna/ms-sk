package com.mysillydreams.userservice.web.inventory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.userservice.config.UserIntegrationTestBase;
import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.domain.inventory.InventoryItem;
import com.mysillydreams.userservice.domain.inventory.InventoryProfile;
import com.mysillydreams.userservice.domain.inventory.StockTransaction;
import com.mysillydreams.userservice.domain.inventory.TransactionType;
import com.mysillydreams.userservice.dto.inventory.InventoryItemDto;
import com.mysillydreams.userservice.dto.inventory.StockAdjustmentRequest;
import com.mysillydreams.userservice.repository.UserRepository;
import com.mysillydreams.userservice.repository.inventory.InventoryItemRepository;
import com.mysillydreams.userservice.repository.inventory.InventoryProfileRepository;
import com.mysillydreams.userservice.repository.inventory.StockTransactionRepository;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker; // For BROKER_ADDRESS_PLACEHOLDER
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;


import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EmbeddedKafka(partitions = 1,
               topics = {"${inventory.topic.itemCreated:inventory.item.created.v1}",
                         "${inventory.topic.stockAdjusted:inventory.stock.adjusted.v1}"},
               brokerProperties = {"listeners=PLAINTEXT://localhost:9101", "port=9101"}) // Another port
public class InventoryControllerIntegrationTest extends UserIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private InventoryProfileRepository inventoryProfileRepository;
    @Autowired
    private InventoryItemRepository inventoryItemRepository;
    @Autowired
    private StockTransactionRepository stockTransactionRepository;

    @Value("${inventory.topic.itemCreated}")
    private String itemCreatedTopic;
    @Value("${inventory.topic.stockAdjusted}")
    private String stockAdjustedTopic;
    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    private UserEntity testUser;
    private InventoryProfile testInventoryProfile;
    private UUID testInventoryProfileId;

    @BeforeEach
    void setUpEntities() {
        stockTransactionRepository.deleteAllInBatch();
        inventoryItemRepository.deleteAllInBatch();
        inventoryProfileRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        testUser = new UserEntity();
        testUser.setReferenceId("inv-ctrl-user-" + UUID.randomUUID());
        testUser.setEmail(testUser.getReferenceId() + "@example.com");
        testUser = userRepository.saveAndFlush(testUser);

        testInventoryProfile = new InventoryProfile();
        testInventoryProfile.setUser(testUser);
        testInventoryProfile = inventoryProfileRepository.saveAndFlush(testInventoryProfile);
        testInventoryProfileId = testInventoryProfile.getId();
    }

    @AfterEach
    void tearDownData() {
        stockTransactionRepository.deleteAllInBatch();
        inventoryItemRepository.deleteAllInBatch();
        inventoryProfileRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void addItem_success_createsItemAndPublishesEvent() throws Exception {
        InventoryItemDto requestDto = new InventoryItemDto();
        requestDto.setSku("CTRL-SKU-001");
        requestDto.setName("Controller Test Item");
        requestDto.setDescription("Item via controller test");
        requestDto.setQuantityOnHand(10); // Service should set this if not from DTO, or DTO default
        requestDto.setReorderLevel(5);

        MvcResult result = mockMvc.perform(post("/inventory/items")
                        .header("X-Inventory-Profile-Id", testInventoryProfileId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku", is(requestDto.getSku())))
                .andExpect(jsonPath("$.name", is(requestDto.getName())))
                .andExpect(jsonPath("$.quantityOnHand", is(requestDto.getQuantityOnHand()))) // Assuming service sets from DTO
                .andReturn();

        InventoryItemDto responseDto = objectMapper.readValue(result.getResponse().getContentAsString(), InventoryItemDto.class);
        UUID newItemId = responseDto.getId();

        // Verify DB
        Optional<InventoryItem> itemOpt = inventoryItemRepository.findById(newItemId);
        assertThat(itemOpt).isPresent();
        assertThat(itemOpt.get().getOwner().getId()).isEqualTo(testInventoryProfileId);

        // Verify Kafka event for item created
        try (KafkaConsumer<String, String> consumer = (KafkaConsumer<String, String>) consumerFactory.createConsumer("item-created-ctrl-group", null, null, KafkaTestUtils.consumerProps("item-created-ctrl-group", "true", EmbeddedKafkaBroker.BROKER_ADDRESS_PLACEHOLDER))) {
            consumer.subscribe(Collections.singletonList(itemCreatedTopic));
            ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10).toMillis(),1);
            assertThat(records.count()).isEqualTo(1);
            ConsumerRecord<String, String> consumedRecord = records.iterator().next();
            assertThat(consumedRecord.key()).isEqualTo(newItemId.toString());
            Map<String, Object> payload = objectMapper.readValue(consumedRecord.value(), new TypeReference<>() {});
            assertThat(payload.get("itemId")).isEqualTo(newItemId.toString());
            assertThat(payload.get("sku")).isEqualTo(requestDto.getSku());
            assertThat(payload.get("inventoryProfileId")).isEqualTo(testInventoryProfileId.toString());
        }
    }

    @Test
    void listItems_success() throws Exception {
        InventoryItem item1 = new InventoryItem();
        item1.setOwner(testInventoryProfile);
        item1.setSku("LIST-SKU-1");
        item1.setName("List Item 1");
        inventoryItemRepository.save(item1);

        InventoryItem item2 = new InventoryItem();
        item2.setOwner(testInventoryProfile);
        item2.setSku("LIST-SKU-2");
        item2.setName("List Item 2");
        inventoryItemRepository.save(item2);
        inventoryItemRepository.flush();


        mockMvc.perform(get("/inventory/items")
                        .header("X-Inventory-Profile-Id", testInventoryProfileId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].sku", anyOf(is("LIST-SKU-1"), is("LIST-SKU-2"))))
                .andExpect(jsonPath("$[1].sku", anyOf(is("LIST-SKU-1"), is("LIST-SKU-2"))));
    }

    @Test
    void adjustStock_success_updatesQuantityAndPublishesEvent() throws Exception {
        InventoryItem item = new InventoryItem();
        item.setOwner(testInventoryProfile);
        item.setSku("ADJUST-SKU-001");
        item.setName("Stock Adjust Item");
        item.setQuantityOnHand(50);
        item.setReorderLevel(10);
        item = inventoryItemRepository.saveAndFlush(item);
        UUID itemId = item.getId();

        StockAdjustmentRequest adjustmentRequest = new StockAdjustmentRequest();
        adjustmentRequest.setType(TransactionType.ISSUE);
        adjustmentRequest.setQuantity(10);

        mockMvc.perform(post("/inventory/items/{itemId}/adjust", itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adjustmentRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(itemId.toString())))
                .andExpect(jsonPath("$.quantityOnHand", is(40))); // 50 - 10

        // Verify DB
        Optional<InventoryItem> updatedItemOpt = inventoryItemRepository.findById(itemId);
        assertThat(updatedItemOpt).isPresent();
        assertThat(updatedItemOpt.get().getQuantityOnHand()).isEqualTo(40);

        List<StockTransaction> transactions = stockTransactionRepository.findByItemId(itemId, Sort.by("timestamp"));
        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getType()).isEqualTo(TransactionType.ISSUE);
        assertThat(transactions.get(0).getQuantity()).isEqualTo(10);

        // Verify Kafka event for stock adjusted
         try (KafkaConsumer<String, String> consumer = (KafkaConsumer<String, String>) consumerFactory.createConsumer("stock-adj-ctrl-group", null, null, KafkaTestUtils.consumerProps("stock-adj-ctrl-group", "true", EmbeddedKafkaBroker.BROKER_ADDRESS_PLACEHOLDER))) {
            consumer.subscribe(Collections.singletonList(stockAdjustedTopic));
            ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10).toMillis(),1);
            assertThat(records.count()).isEqualTo(1);
            ConsumerRecord<String, String> consumedRecord = records.iterator().next();

            assertThat(consumedRecord.key()).isEqualTo(itemId.toString());
            Map<String, Object> payload = objectMapper.readValue(consumedRecord.value(), new TypeReference<>() {});
            assertThat(payload.get("itemId")).isEqualTo(itemId.toString());
            assertThat(payload.get("transactionType")).isEqualTo(TransactionType.ISSUE.toString());
            assertThat(payload.get("quantityAdjusted")).isEqualTo(10);
            assertThat(payload.get("newQuantityOnHand")).isEqualTo(40);
        }
    }
}
