package com.ecommerce.vendorfulfillmentservice.integration;

import com.ecommerce.vendorfulfillmentservice.AbstractIntegrationTest;
import com.ecommerce.vendorfulfillmentservice.controller.dto.ShipAssignmentRequest; // For /ship test
import com.ecommerce.vendorfulfillmentservice.entity.AssignmentStatus;
import com.ecommerce.vendorfulfillmentservice.entity.VendorOrderAssignment;
import com.ecommerce.vendorfulfillmentservice.event.avro.VendorOrderAcknowledgedEvent;
import com.ecommerce.vendorfulfillmentservice.event.avro.VendorOrderShippedEvent; // For /ship test
import com.ecommerce.vendorfulfillmentservice.event.avro.ShipmentNotificationRequestedEvent; // For /ship test
import com.ecommerce.vendorfulfillmentservice.repository.VendorOrderAssignmentRepository;
// import com.ecommerce.vendorfulfillmentservice.service.VendorAssignmentService; // Not needed directly if using repo to setup
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
    @Value("${app.kafka.topic.vendor-order-shipped}")
    private String shippedOutputTopic;
    @Value("${app.kafka.topic.shipment-notification-requested}")
    private String shipmentNotificationRequestedTopic;


    @Value("${spring.kafka.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    private KafkaMessageListenerContainer<String, VendorOrderAcknowledgedEvent> acknowledgedListenerContainer;
    private final BlockingQueue<ConsumerRecord<String, VendorOrderAcknowledgedEvent>> acknowledgedRecords = new LinkedBlockingQueue<>();

    private KafkaMessageListenerContainer<String, VendorOrderShippedEvent> shippedListenerContainer;
    private final BlockingQueue<ConsumerRecord<String, VendorOrderShippedEvent>> shippedRecords = new LinkedBlockingQueue<>();

    private KafkaMessageListenerContainer<String, ShipmentNotificationRequestedEvent> shipmentNotificationListenerContainer;
    private final BlockingQueue<ConsumerRecord<String, ShipmentNotificationRequestedEvent>> shipmentNotificationRecords = new LinkedBlockingQueue<>();

    private VendorOrderAssignment testAssignment; // Used across tests, setup individually or in @BeforeEach

    // Helper to create a standard Kafka consumer factory for Avro events
    private <T extends org.apache.avro.specific.SpecificRecord> DefaultKafkaConsumerFactory<String, T> createAvroConsumerFactory() {
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, getKafkaBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID().toString());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        consumerProps.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
        return new DefaultKafkaConsumerFactory<>(consumerProps);
    }

    @BeforeEach
    void setUp() {
        // Listener for Acknowledged Events
        DefaultKafkaConsumerFactory<String, VendorOrderAcknowledgedEvent> ackConsumerFactory = createAvroConsumerFactory();
        ContainerProperties ackContainerProps = new ContainerProperties(acknowledgedOutputTopic);
        acknowledgedListenerContainer = new KafkaMessageListenerContainer<>(ackConsumerFactory, ackContainerProps);
        acknowledgedListenerContainer.setupMessageListener((MessageListener<String, VendorOrderAcknowledgedEvent>) acknowledgedRecords::add);
        acknowledgedListenerContainer.start();

        // Listener for Shipped Events
        DefaultKafkaConsumerFactory<String, VendorOrderShippedEvent> shippedConsumerFactory = createAvroConsumerFactory();
        ContainerProperties shippedContainerProps = new ContainerProperties(shippedOutputTopic);
        shippedListenerContainer = new KafkaMessageListenerContainer<>(shippedConsumerFactory, shippedContainerProps);
        shippedListenerContainer.setupMessageListener((MessageListener<String, VendorOrderShippedEvent>) shippedRecords::add);
        shippedListenerContainer.start();

        // Listener for Shipment Notification Requested Events
        DefaultKafkaConsumerFactory<String, ShipmentNotificationRequestedEvent> shipmentNotificationConsumerFactory = createAvroConsumerFactory();
        ContainerProperties shipmentNotificationContainerProps = new ContainerProperties(shipmentNotificationRequestedTopic);
        shipmentNotificationListenerContainer = new KafkaMessageListenerContainer<>(shipmentNotificationConsumerFactory, shipmentNotificationContainerProps);
        shipmentNotificationListenerContainer.setupMessageListener((MessageListener<String, ShipmentNotificationRequestedEvent>) shipmentNotificationRecords::add);
        shipmentNotificationListenerContainer.start();
    }

    @AfterEach
    void tearDown() {
        if (acknowledgedListenerContainer != null) acknowledgedListenerContainer.stop();
        if (shippedListenerContainer != null) shippedListenerContainer.stop();
        if (shipmentNotificationListenerContainer != null) shipmentNotificationListenerContainer.stop();

        assignmentRepository.deleteAll();
        acknowledgedRecords.clear();
        shippedRecords.clear();
        shipmentNotificationRecords.clear();
    }

    private VendorOrderAssignment createAndSaveTestAssignment(AssignmentStatus status) {
        VendorOrderAssignment assignment = VendorOrderAssignment.builder()
                .orderId(UUID.randomUUID())
                .vendorId(UUID.randomUUID())
                .status(status)
                .build();
        return assignmentRepository.saveAndFlush(assignment);
    }

    @Test
    @WithMockUser(roles = {"VENDOR"})
    void shouldUpdateStatusAndPublishEvent_whenAckEndpointCalled() throws InterruptedException {
        testAssignment = createAndSaveTestAssignment(AssignmentStatus.ASSIGNED);
        String ackUrl = "/fulfillment/assignments/" + testAssignment.getId() + "/ack";

        ResponseEntity<VendorOrderAssignment> response = restTemplate.postForEntity(ackUrl, null, VendorOrderAssignment.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertNotNull(response.getBody());
        assertThat(response.getBody().getStatus()).isEqualTo(AssignmentStatus.ACKNOWLEDGED);

        VendorOrderAssignment updatedAssignment = assignmentRepository.findById(testAssignment.getId()).orElse(null);
        assertNotNull(updatedAssignment);
        assertThat(updatedAssignment.getStatus()).isEqualTo(AssignmentStatus.ACKNOWLEDGED);

        ConsumerRecord<String, VendorOrderAcknowledgedEvent> consumedRecord = acknowledgedRecords.poll(10, TimeUnit.SECONDS);
        assertNotNull(consumedRecord, "No VendorOrderAcknowledgedEvent received from Kafka");
        VendorOrderAcknowledgedEvent publishedEvent = consumedRecord.value();
        assertThat(publishedEvent.getAssignmentId()).isEqualTo(testAssignment.getId().toString());
        assertThat(publishedEvent.getStatus()).isEqualTo(AssignmentStatus.ACKNOWLEDGED.name());
    }

    @Test
    @WithMockUser(roles = {"VENDOR"})
    void shouldShipOrderAndPublishEvents_whenShipEndpointCalled() throws InterruptedException {
        // Prerequisite: Assignment should be in PACKED status to be shipped.
        testAssignment = createAndSaveTestAssignment(AssignmentStatus.PACKED);
        String trackingNo = "TRACK123XYZ";
        ShipAssignmentRequest shipRequest = new ShipAssignmentRequest(trackingNo);
        String shipUrl = "/fulfillment/assignments/" + testAssignment.getId() + "/ship";

        ResponseEntity<VendorOrderAssignment> response = restTemplate.postForEntity(shipUrl, shipRequest, VendorOrderAssignment.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertNotNull(response.getBody());
        assertThat(response.getBody().getStatus()).isEqualTo(AssignmentStatus.SHIPPED);
        assertThat(response.getBody().getTrackingNo()).isEqualTo(trackingNo);

        VendorOrderAssignment updatedAssignment = assignmentRepository.findById(testAssignment.getId()).orElse(null);
        assertNotNull(updatedAssignment);
        assertThat(updatedAssignment.getStatus()).isEqualTo(AssignmentStatus.SHIPPED);
        assertThat(updatedAssignment.getTrackingNo()).isEqualTo(trackingNo);

        // Verify VendorOrderShippedEvent
        ConsumerRecord<String, VendorOrderShippedEvent> shippedRecord = shippedRecords.poll(10, TimeUnit.SECONDS);
        assertNotNull(shippedRecord, "No VendorOrderShippedEvent received");
        VendorOrderShippedEvent shippedEvent = shippedRecord.value();
        assertThat(shippedEvent.getAssignmentId()).isEqualTo(testAssignment.getId().toString());
        assertThat(shippedEvent.getStatus()).isEqualTo(AssignmentStatus.SHIPPED.name());
        assertThat(shippedEvent.getTrackingNo()).isEqualTo(trackingNo);

        // Verify ShipmentNotificationRequestedEvent
        ConsumerRecord<String, ShipmentNotificationRequestedEvent> notificationRecord = shipmentNotificationRecords.poll(10, TimeUnit.SECONDS);
        assertNotNull(notificationRecord, "No ShipmentNotificationRequestedEvent received");
        ShipmentNotificationRequestedEvent notificationEvent = notificationRecord.value();
        assertThat(notificationEvent.getAssignmentId()).isEqualTo(testAssignment.getId().toString());
        assertThat(notificationEvent.getOrderId()).isEqualTo(testAssignment.getOrderId().toString());
        assertThat(notificationEvent.getTrackingNo()).isEqualTo(trackingNo);
        assertThat(notificationEvent.getNotificationType()).isEqualTo("CUSTOMER_SHIPMENT_CONFIRMATION");
    }
}
