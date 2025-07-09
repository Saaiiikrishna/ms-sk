package com.ecommerce.vendorfulfillmentservice.integration;

import com.ecommerce.vendorfulfillmentservice.AbstractIntegrationTest;
import com.ecommerce.vendorfulfillmentservice.entity.AssignmentStatus;
import com.ecommerce.vendorfulfillmentservice.entity.VendorOrderAssignment;
import com.ecommerce.vendorfulfillmentservice.event.avro.VendorOrderAcknowledgedEvent;
import com.ecommerce.vendorfulfillmentservice.repository.VendorOrderAssignmentRepository;
import com.ecommerce.vendorfulfillmentservice.service.VendorAssignmentService; // To create initial assignment
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.security.test.context.support.WithMockUser; // For API auth

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AssignmentActionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate; // For making HTTP calls

    @Autowired
    private VendorOrderAssignmentRepository assignmentRepository;

    // We need to create an initial assignment for these tests.
    // Directly using the repository or a minimal setup via service.
    // For this test, let's assume an assignment already exists.

    @Value("${app.kafka.topic.vendor-order-acknowledged}")
    private String acknowledgedOutputTopic;

    @Value("${spring.kafka.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    private KafkaMessageListenerContainer<String, VendorOrderAcknowledgedEvent> acknowledgedListenerContainer;
    private final BlockingQueue<ConsumerRecord<String, VendorOrderAcknowledgedEvent>> acknowledgedRecords = new LinkedBlockingQueue<>();

    private VendorOrderAssignment testAssignment;

    @BeforeEach
    void setUp() {
        // Create a test assignment directly in DB for ACK test
        testAssignment = VendorOrderAssignment.builder()
                .orderId(UUID.randomUUID())
                .vendorId(UUID.randomUUID()) // This would be the vendor's ID for whom the mock user is authenticated
                .status(AssignmentStatus.ASSIGNED)
                .build();
        testAssignment = assignmentRepository.saveAndFlush(testAssignment);


        // Consumer for VendorOrderAcknowledgedEvent (AVRO)
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, getKafkaBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-ack-group-" + UUID.randomUUID().toString());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        consumerProps.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);

        DefaultKafkaConsumerFactory<String, VendorOrderAcknowledgedEvent> consumerFactory =
                new DefaultKafkaConsumerFactory<>(consumerProps);
        ContainerProperties containerProps = new ContainerProperties(acknowledgedOutputTopic);
        acknowledgedListenerContainer = new KafkaMessageListenerContainer<>(consumerFactory, containerProps);
        acknowledgedListenerContainer.setupMessageListener((MessageListener<String, VendorOrderAcknowledgedEvent>) acknowledgedRecords::add);
        acknowledgedListenerContainer.start();
    }

    @AfterEach
    void tearDown() {
        if (acknowledgedListenerContainer != null) {
            acknowledgedListenerContainer.stop();
        }
        assignmentRepository.deleteAll(); // Clean up database
        acknowledgedRecords.clear();
    }

    @Test
    @WithMockUser(roles = {"VENDOR"}) // Simulate a VENDOR making the call
    void shouldUpdateStatusAndPublishEvent_whenAckEndpointCalled() throws InterruptedException {
        // 1. Call the /ack endpoint
        String ackUrl = "/fulfillment/assignments/" + testAssignment.getId() + "/ack";

        // TestRestTemplate doesn't automatically include CSRF tokens if CSRF is enabled.
        // For @SpringBootTest with WebEnvironment.RANDOM_PORT, CSRF might be active.
        // However, SecurityConfig disables CSRF. If it were enabled, special handling for CSRF in TestRestTemplate would be needed.
        ResponseEntity<VendorOrderAssignment> response = restTemplate.postForEntity(ackUrl, null, VendorOrderAssignment.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertNotNull(response.getBody());
        assertThat(response.getBody().getStatus()).isEqualTo(AssignmentStatus.ACKNOWLEDGED);

        // 2. Verify database state
        VendorOrderAssignment updatedAssignment = assignmentRepository.findById(testAssignment.getId()).orElse(null);
        assertNotNull(updatedAssignment);
        assertThat(updatedAssignment.getStatus()).isEqualTo(AssignmentStatus.ACKNOWLEDGED);

        // 3. Verify output event is published
        ConsumerRecord<String, VendorOrderAcknowledgedEvent> consumedRecord = acknowledgedRecords.poll(10, TimeUnit.SECONDS);
        assertNotNull(consumedRecord, "No VendorOrderAcknowledgedEvent received from Kafka");
        VendorOrderAcknowledgedEvent publishedEvent = consumedRecord.value();
        assertNotNull(publishedEvent);
        assertThat(publishedEvent.getAssignmentId()).isEqualTo(testAssignment.getId().toString());
        assertThat(publishedEvent.getOrderId()).isEqualTo(testAssignment.getOrderId().toString());
        assertThat(publishedEvent.getVendorId()).isEqualTo(testAssignment.getVendorId().toString());
        assertThat(publishedEvent.getStatus()).isEqualTo(AssignmentStatus.ACKNOWLEDGED.name());
    }
}
