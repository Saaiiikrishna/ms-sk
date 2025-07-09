package com.ecommerce.vendorfulfillmentservice.integration;

import com.ecommerce.vendorfulfillmentservice.AbstractIntegrationTest;
import com.ecommerce.vendorfulfillmentservice.entity.AssignmentStatus;
import com.ecommerce.vendorfulfillmentservice.entity.VendorOrderAssignment;
import com.ecommerce.vendorfulfillmentservice.event.OrderReservationSucceededEvent; // Internal DTO
import com.ecommerce.vendorfulfillmentservice.event.avro.VendorOrderAssignedEvent; // Expected Avro output
import com.ecommerce.vendorfulfillmentservice.repository.VendorOrderAssignmentRepository;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.serializer.JsonSerializer; // For sending the input DTO

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class OrderAssignmentIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, Object> jsonKafkaTemplate; // For sending JSON DTO

    private KafkaTemplate<String, SpecificRecord> avroKafkaTemplate; // For general Avro if needed, but consumer is specific

    @Autowired
    private VendorOrderAssignmentRepository assignmentRepository;

    @Value("${app.kafka.topic.order-reservation-succeeded}")
    private String inputTopic;

    @Value("${app.kafka.topic.vendor-order-assigned}")
    private String outputTopic;

    @Value("${spring.kafka.properties.schema.registry.url}")
    private String schemaRegistryUrl;


    private KafkaMessageListenerContainer<String, VendorOrderAssignedEvent> outputListenerContainer;
    private final BlockingQueue<ConsumerRecord<String, VendorOrderAssignedEvent>> outputRecords = new LinkedBlockingQueue<>();

    @BeforeEach
    void setUp() {
        // Producer for Avro (if we were to produce generic Avro records not via app's outbox)
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, getKafkaBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, io.confluent.kafka.serializers.KafkaAvroSerializer.class);
        producerProps.put("schema.registry.url", schemaRegistryUrl); // Use mock URL from AbstractIntegrationTest
        ProducerFactory<String, SpecificRecord> avroProducerFactory = new DefaultKafkaProducerFactory<>(producerProps);
        avroKafkaTemplate = new KafkaTemplate<>(avroProducerFactory);


        // Consumer for VendorOrderAssignedEvent (AVRO)
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, getKafkaBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID().toString());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl); // mock URL
        consumerProps.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
        // For Avro, ensure the package of generated class is trusted if not using SPECIFIC_AVRO_READER_CONFIG with GenericRecord
        // props.put(JsonDeserializer.TRUSTED_PACKAGES, "*"); // Not for Avro specifically, but good for JSON


        DefaultKafkaConsumerFactory<String, VendorOrderAssignedEvent> consumerFactory =
                new DefaultKafkaConsumerFactory<>(consumerProps);
        ContainerProperties containerProps = new ContainerProperties(outputTopic);
        outputListenerContainer = new KafkaMessageListenerContainer<>(consumerFactory, containerProps);
        outputListenerContainer.setupMessageListener((MessageListener<String, VendorOrderAssignedEvent>) outputRecords::add);
        outputListenerContainer.start();
    }

    @AfterEach
    void tearDown() {
        if (outputListenerContainer != null) {
            outputListenerContainer.stop();
        }
        assignmentRepository.deleteAll(); // Clean up database
        outputRecords.clear();
    }

    @Test
    void shouldCreateAssignmentAndPublishEvent_whenOrderReservationSucceededEventConsumed() throws InterruptedException {
        // 1. Prepare input event (using the internal DTO, sent as JSON)
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        OrderReservationSucceededEvent inputEvent = new OrderReservationSucceededEvent(
                eventId, orderId, customerId, OffsetDateTime.now()
        );

        // 2. Send input event to Kafka
        // Need a KafkaTemplate that serializes OrderReservationSucceededEvent as JSON
        // The main app uses JsonDeserializer, so we should send JSON.
        // The existing `jsonKafkaTemplate` bean might be <String, Object> or specialized.
        // Let's reconfigure one here for clarity for this specific DTO.
        Map<String, Object> jsonProducerProps = new HashMap<>();
        jsonProducerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, getKafkaBootstrapServers());
        jsonProducerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        jsonProducerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class); // Spring's JSON serializer
        ProducerFactory<String, OrderReservationSucceededEvent> jsonPf = new DefaultKafkaProducerFactory<>(jsonProducerProps);
        KafkaTemplate<String, OrderReservationSucceededEvent> specializedJsonKafkaTemplate = new KafkaTemplate<>(jsonPf);

        specializedJsonKafkaTemplate.send(inputTopic, orderId.toString(), inputEvent);

        // 3. Verify output event is published
        ConsumerRecord<String, VendorOrderAssignedEvent> consumedRecord = outputRecords.poll(15, TimeUnit.SECONDS); // Wait up to 10s
        assertNotNull(consumedRecord, "No VendorOrderAssignedEvent received from Kafka");
        VendorOrderAssignedEvent publishedEvent = consumedRecord.value();
        assertNotNull(publishedEvent);
        assertThat(publishedEvent.getOrderId()).isEqualTo(orderId.toString());
        assertThat(publishedEvent.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED.name());
        UUID assignmentIdFromEvent = UUID.fromString(publishedEvent.getAssignmentId());

        // 4. Verify database state
        // Wait a bit for transactional commit if needed, though Kafka ack implies commit for this app's flow.
        Thread.sleep(500); // Small grace period for DB commit visibility

        VendorOrderAssignment assignment = assignmentRepository.findById(assignmentIdFromEvent)
                .orElse(null);
        assertNotNull(assignment, "VendorOrderAssignment not found in database");
        assertThat(assignment.getOrderId()).isEqualTo(orderId);
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(assignment.getVendorId()).isNotNull(); // Vendor ID is determined by service logic

        // Verify idempotency: Send the same event again
        specializedJsonKafkaTemplate.send(inputTopic, orderId.toString(), inputEvent);
        ConsumerRecord<String, VendorOrderAssignedEvent> secondConsumedRecord = outputRecords.poll(5, TimeUnit.SECONDS); // Shorter timeout
        assertThat(secondConsumedRecord).isNull("Second identical event should not produce a new VendorOrderAssignedEvent due to idempotency");

        long assignmentCount = assignmentRepository.count();
        assertThat(assignmentCount).isEqualTo(1); // Should still be only one assignment
    }
}
