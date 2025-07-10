package com.mysillydreams.auth.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;


import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;


@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = {KafkaPublisher.class, KafkaAutoConfiguration.class}, // Load KafkaAutoConfiguration for KafkaTemplate
    properties = {
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
        "spring.kafka.producer.properties.spring.json.add.type.headers=false"
    }
)
@EmbeddedKafka(
    partitions = 1,
    brokerProperties = {"listeners=PLAINTEXT://localhost:9093", "port=9093"}, // Use a different port than default
    topics = {KafkaPublisherTest.TEST_TOPIC} // Define topic for test
)
@DirtiesContext // Ensures broker is fresh for each test class
public class KafkaPublisherTest {

    private static final Logger logger = LoggerFactory.getLogger(KafkaPublisherTest.class);
    static final String TEST_TOPIC = "test.events";

    @Autowired
    private KafkaPublisher kafkaPublisher;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private KafkaMessageListenerContainer<String, String> container;
    private BlockingQueue<ConsumerRecord<String, String>> consumerRecords;
    private ObjectMapper objectMapper;


    @BeforeEach
    void setUp() {
        consumerRecords = new LinkedBlockingQueue<>();

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("testGroup", "true", embeddedKafkaBroker);
        // Use StringDeserializer for value as JsonSerializer on producer side produces a String.
        consumerProps.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");


        DefaultKafkaConsumerFactory<String, String> consumerFactory = new DefaultKafkaConsumerFactory<>(consumerProps);
        ContainerProperties containerProperties = new ContainerProperties(TEST_TOPIC);
        container = new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);
        container.setupMessageListener((MessageListener<String, String>) record -> {
            logger.debug("Test consumer received record: {}", record);
            consumerRecords.add(record);
        });
        container.start();
        ContainerTestUtils.waitForAssignment(container, embeddedKafkaBroker.getPartitionsPerTopic());

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule()); // For Instant serialization/deserialization
    }


    @AfterAll
    static void tearDownAll(@Autowired EmbeddedKafkaBroker broker) {
        if (broker != null) {
            broker.destroy();
        }
    }

    @BeforeEach
    void clearQueue() {
        consumerRecords.clear(); // Clear records before each test if container is reused
    }


    @Test
    void publishEvent_shouldSendMessageToKafka() throws InterruptedException, JsonProcessingException {
        // Given
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId.toString());
        payload.put("action", "TEST_ACTION");
        payload.put("timestamp", now.toString()); // Send as string to match typical JSON

        String eventKey = "test.event.type";
        String messageKey = UUID.randomUUID().toString();

        // When
        kafkaPublisher.publishEvent(TEST_TOPIC, messageKey, eventKey, payload);

        // Then
        ConsumerRecord<String, String> received = consumerRecords.poll(10, TimeUnit.SECONDS); // Wait up to 10 seconds
        assertNotNull(received, "Message should be received from Kafka topic");
        assertThat(received.key()).isEqualTo(messageKey);
        assertThat(received.topic()).isEqualTo(TEST_TOPIC);

        // Deserialize payload
        @SuppressWarnings("unchecked")
        Map<String, Object> receivedPayload = objectMapper.readValue(received.value(), Map.class);
        assertThat(receivedPayload.get("userId")).isEqualTo(userId.toString());
        assertThat(receivedPayload.get("action")).isEqualTo("TEST_ACTION");
        assertThat(receivedPayload.get("timestamp")).isEqualTo(now.toString());

        logger.info("Successfully consumed message: Key='{}', Payload='{}'", received.key(), received.value());
    }

    @Test
    void publishEvent_withRandomKey_shouldSendMessageToKafka() throws InterruptedException, JsonProcessingException {
        // Given
        Map<String, Object> payload = Map.of("data", "some_value", "id", 123);
        String eventKey = "another.test.event";

        // When
        kafkaPublisher.publishEvent(TEST_TOPIC, eventKey, payload);

        // Then
        ConsumerRecord<String, String> received = consumerRecords.poll(10, TimeUnit.SECONDS);
        assertNotNull(received, "Message should be received");
        assertNotNull(received.key(), "Message should have a randomly generated key"); // Check key is present
        assertThat(received.topic()).isEqualTo(TEST_TOPIC);

        @SuppressWarnings("unchecked")
        Map<String, Object> receivedPayload = objectMapper.readValue(received.value(), Map.class);
        assertThat(receivedPayload.get("data")).isEqualTo("some_value");
        assertThat(receivedPayload.get("id")).isEqualTo(123);
    }
}
