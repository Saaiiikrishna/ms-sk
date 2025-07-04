package com.mysillydreams.catalogservice.service;

import com.mysillydreams.catalogservice.domain.model.CatalogItemEntity;
import com.mysillydreams.catalogservice.domain.model.CategoryEntity;
import com.mysillydreams.catalogservice.domain.model.ItemType;
import com.mysillydreams.catalogservice.domain.model.StockLevelEntity;
import com.mysillydreams.catalogservice.domain.repository.CatalogItemRepository;
import com.mysillydreams.catalogservice.domain.repository.CategoryRepository;
import com.mysillydreams.catalogservice.domain.repository.StockLevelRepository;
import com.mysillydreams.catalogservice.domain.repository.StockTransactionRepository;
import com.mysillydreams.catalogservice.dto.StockAdjustmentRequest;
import com.mysillydreams.catalogservice.dto.StockAdjustmentType;
import com.mysillydreams.catalogservice.dto.StockLevelDto;
import com.mysillydreams.catalogservice.exception.InvalidRequestException;
import com.mysillydreams.catalogservice.kafka.event.StockLevelChangedEvent;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;


import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {"${app.kafka.topic.stock-changed}"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class StockServiceIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> postgresqlContainer =
            new PostgreSQLContainer<>("postgres:15.3-alpine")
                    .withDatabaseName("test_catalog_db_stock_int")
                    .withUsername("testuser")
                    .withPassword("testpassword");

    static {
        System.setProperty("spring.datasource.url", postgresqlContainer.getJdbcUrl());
        System.setProperty("spring.datasource.username", postgresqlContainer.getUsername());
        System.setProperty("spring.datasource.password", postgresqlContainer.getPassword());
        // Ensure Testcontainers JDBC driver is used by Spring Boot if not already default for "tc:" prefix
        System.setProperty("spring.datasource.driver-class-name", "org.testcontainers.jdbc.ContainerDatabaseDriver");
    }

    @Autowired private StockService stockService;
    @Autowired private CatalogItemRepository itemRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private StockLevelRepository stockLevelRepository;
    @Autowired private StockTransactionRepository stockTransactionRepository;
    @Autowired private EmbeddedKafkaBroker embeddedKafkaBroker;
    @Autowired private PlatformTransactionManager transactionManager;


    @Value("${app.kafka.topic.stock-changed}")
    private String stockChangedTopic;

    private KafkaMessageListenerContainer<String, StockLevelChangedEvent> listenerContainer;
    private BlockingQueue<ConsumerRecord<String, StockLevelChangedEvent>> consumerRecords;

    private CatalogItemEntity product1;

    @BeforeEach
    void setUp() {
        consumerRecords = new LinkedBlockingQueue<>();
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("stockTestGroup", "true", embeddedKafkaBroker);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.mysillydreams.catalogservice.kafka.event");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, StockLevelChangedEvent.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);


        DefaultKafkaConsumerFactory<String, StockLevelChangedEvent> cf =
                new DefaultKafkaConsumerFactory<>(consumerProps, new org.apache.kafka.common.serialization.StringDeserializer(), new JsonDeserializer<>(StockLevelChangedEvent.class, false));

        ContainerProperties containerProps = new ContainerProperties(stockChangedTopic);
        listenerContainer = new KafkaMessageListenerContainer<>(cf, containerProps);
        listenerContainer.setupMessageListener((MessageListener<String, StockLevelChangedEvent>) consumerRecords::add);
        listenerContainer.start();
        ContainerTestUtils.waitForAssignment(listenerContainer, embeddedKafkaBroker.getPartitionsPerTopic());

        // Clean up DB
        stockTransactionRepository.deleteAll();
        stockLevelRepository.deleteAll();
        itemRepository.deleteAll();
        categoryRepository.deleteAll();

        CategoryEntity cat = categoryRepository.save(CategoryEntity.builder().name("Electronics").type(ItemType.PRODUCT).path("/el/").build());
        product1 = itemRepository.save(CatalogItemEntity.builder().category(cat).sku("INT-P1").name("Integration Product 1").itemType(ItemType.PRODUCT).basePrice(BigDecimal.TEN).build());
        // Initial stock record is usually created by ItemService.createItem. Here we set it up manually for test.
        stockLevelRepository.save(StockLevelEntity.builder().catalogItem(product1).quantityOnHand(100).reorderLevel(10).build());
    }

    @AfterEach
    void tearDown() {
        if (listenerContainer != null) {
            listenerContainer.stop();
        }
    }

    @Test
    void adjustStock_receive_updatesDbAndPublishesEvent() throws InterruptedException {
        StockAdjustmentRequest request = StockAdjustmentRequest.builder()
                .itemId(product1.getId()).adjustmentType(StockAdjustmentType.RECEIVE)
                .quantity(50).reason("Test receive").build();

        stockService.adjustStock(request);

        StockLevelEntity updatedStock = stockLevelRepository.findByCatalogItemId(product1.getId()).orElseThrow();
        assertThat(updatedStock.getQuantityOnHand()).isEqualTo(150); // 100 + 50

        assertThat(stockTransactionRepository.findByCatalogItemIdOrderByTransactionTimeDesc(product1.getId())).hasSize(1);

        ConsumerRecord<String, StockLevelChangedEvent> record = consumerRecords.poll(5, TimeUnit.SECONDS);
        assertNotNull(record, "Kafka message not received");
        StockLevelChangedEvent event = record.value();
        assertThat(event.getItemId()).isEqualTo(product1.getId());
        assertThat(event.getQuantityBefore()).isEqualTo(100);
        assertThat(event.getQuantityAfter()).isEqualTo(150);
        assertThat(event.getQuantityChanged()).isEqualTo(50);
        assertThat(event.getAdjustmentType()).isEqualTo(StockAdjustmentType.RECEIVE);
    }

    @Test
    void adjustStock_issue_insufficientStock_throwsAndNoEvent() {
        StockAdjustmentRequest request = StockAdjustmentRequest.builder()
                .itemId(product1.getId()).adjustmentType(StockAdjustmentType.ISSUE)
                .quantity(101).reason("Test issue too many").build(); // Initial stock is 100

        assertThrows(InvalidRequestException.class, () -> stockService.adjustStock(request));

        StockLevelEntity stock = stockLevelRepository.findByCatalogItemId(product1.getId()).orElseThrow();
        assertThat(stock.getQuantityOnHand()).isEqualTo(100); // Unchanged
        assertThat(consumerRecords).isEmpty(); // No event published
    }

    @Test
    void reserveAndReleaseStock_updatesCorrectly() throws InterruptedException {
        // Reserve 20
        stockService.reserveStock(product1.getId(), 20);
        StockLevelEntity stockAfterReserve = stockLevelRepository.findByCatalogItemId(product1.getId()).orElseThrow();
        assertThat(stockAfterReserve.getQuantityOnHand()).isEqualTo(80);
        ConsumerRecord<String, StockLevelChangedEvent> reserveEventRecord = consumerRecords.poll(5, TimeUnit.SECONDS);
        assertNotNull(reserveEventRecord);
        assertThat(reserveEventRecord.value().getQuantityAfter()).isEqualTo(80);
        assertThat(reserveEventRecord.value().getQuantityChanged()).isEqualTo(-20);


        // Release 10
        stockService.releaseStock(product1.getId(), 10);
        StockLevelEntity stockAfterRelease = stockLevelRepository.findByCatalogItemId(product1.getId()).orElseThrow();
        assertThat(stockAfterRelease.getQuantityOnHand()).isEqualTo(90); // 80 + 10
        ConsumerRecord<String, StockLevelChangedEvent> releaseEventRecord = consumerRecords.poll(5, TimeUnit.SECONDS);
        assertNotNull(releaseEventRecord);
        assertThat(releaseEventRecord.value().getQuantityAfter()).isEqualTo(90);
        assertThat(releaseEventRecord.value().getQuantityChanged()).isEqualTo(10);
    }

    @Test
    void concurrentStockAdjustments_optimisticLockingAndRetry() throws InterruptedException {
        // Test optimistic locking and @Retryable behavior
        // This test requires multiple threads trying to update the same stock level.
        // The @Retryable annotation on StockService methods should handle OptimisticLockingFailureException.

        int initialStock = stockLevelRepository.findByCatalogItemId(product1.getId()).get().getQuantityOnHand();
        int numberOfThreads = 5;
        int adjustmentsPerThread = 2; // Each thread tries to issue 1 unit, 2 times
        int totalIssuedQuantity = numberOfThreads * adjustmentsPerThread;

        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        AtomicInteger successfulAdjustments = new AtomicInteger(0);
        AtomicInteger optimisticLockFailuresInTest = new AtomicInteger(0);


        for (int i = 0; i < numberOfThreads; i++) {
            executorService.submit(() -> {
                // Each thread needs its own transaction context if using @Transactional methods directly from service
                // Using TransactionTemplate for programmatic transaction management per thread
                TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
                try {
                    for (int j = 0; j < adjustmentsPerThread; j++) {
                        transactionTemplate.execute(status -> {
                            try {
                                StockAdjustmentRequest request = StockAdjustmentRequest.builder()
                                    .itemId(product1.getId())
                                    .adjustmentType(StockAdjustmentType.ISSUE)
                                    .quantity(1) // Issue 1 unit
                                    .reason("Concurrent test issue")
                                    .build();
                                stockService.adjustStock(request); // This method is @Retryable
                                successfulAdjustments.incrementAndGet();
                            } catch (OptimisticLockingFailureException ole) {
                                // This exception should ideally be caught and retried by Spring @Retryable
                                // If it propagates here, it means retries were exhausted or not configured for this.
                                // However, @Retryable works on beans called from outside.
                                // Here we are calling the method on the autowired bean.
                                optimisticLockFailuresInTest.incrementAndGet();
                                log.error("Optimistic lock failure during concurrent test (propagated): ", ole);
                                status.setRollbackOnly();
                            } catch (Exception e) {
                                log.error("Error during concurrent stock adjustment in thread: ", e);
                                status.setRollbackOnly();
                            }
                            return null;
                        });
                         Thread.sleep(10); // Small delay to increase chance of collision
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS); // Wait for all threads to complete
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);

        StockLevelEntity finalStock = stockLevelRepository.findByCatalogItemId(product1.getId()).orElseThrow();
        int expectedFinalStock = initialStock - totalIssuedQuantity;

        // Assertions
        assertEquals(expectedFinalStock, finalStock.getQuantityOnHand(), "Final stock quantity is incorrect after concurrent adjustments.");
        assertEquals(totalIssuedQuantity, successfulAdjustments.get(), "Number of successful adjustments is incorrect.");
        // Ideally, optimisticLockFailuresInTest should be 0 if @Retryable handles all of them.
        // The number of actual OptimisticLockExceptions that Spring Retry handles internally is not easily visible here.
        // This test primarily verifies the end state and that the operation completed for all attempts.
        log.info("Concurrent test: Optimistic lock failures propagated to test (should be low/zero if retries are effective): {}", optimisticLockFailuresInTest.get());


        // Check number of Kafka messages
        int receivedMessages = 0;
        while(consumerRecords.poll(50, TimeUnit.MILLISECONDS) != null) {
            receivedMessages++;
        }
        assertEquals(totalIssuedQuantity, receivedMessages, "Incorrect number of Kafka messages for stock changes.");
    }
}
