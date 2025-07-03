package com.mysillydreams.userservice.service.delivery;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.domain.delivery.AssignmentStatus;
import com.mysillydreams.userservice.domain.delivery.AssignmentType;
import com.mysillydreams.userservice.domain.delivery.DeliveryProfile;
import com.mysillydreams.userservice.domain.delivery.OrderAssignment;

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
import java.time.Duration; // For KafkaTestUtils.getRecords timeout

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EmbeddedKafka(partitions = 1,
               topics = {
                    "${delivery.topic.orderAssigned:order.assigned.v1}",
                    "${delivery.topic.deliveryStatusChanged:delivery.status.changed.v1}"
               },
               brokerProperties = {"listeners=PLAINTEXT://localhost:9102", "port=9102"}) // Another unique port
public class DeliveryKafkaClientIntegrationTest {

    @Autowired
    private DeliveryKafkaClient deliveryKafkaClient;

    @Value("${delivery.topic.orderAssigned}")
    private String orderAssignedTopic;
    @Value("${delivery.topic.deliveryStatusChanged}")
    private String deliveryStatusChangedTopic;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;
    @Autowired
    private ObjectMapper objectMapper;

    private KafkaConsumer<String, String> consumer;

    private OrderAssignment sampleAssignment;

    @BeforeEach
    void setUp() {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("delivery-kafka-test-group", "true", EmbeddedKafkaBroker.BROKER_ADDRESS_PLACEHOLDER);
        consumerProps.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        consumer = (KafkaConsumer<String, String>) consumerFactory.createConsumer("delivery-kafka-test-group", null, null, consumerProps);

        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        DeliveryProfile profile = new DeliveryProfile();
        profile.setId(UUID.randomUUID());
        profile.setUser(user);

        sampleAssignment = new OrderAssignment();
        sampleAssignment.setId(UUID.randomUUID());
        sampleAssignment.setOrderId(UUID.randomUUID());
        sampleAssignment.setDeliveryProfile(profile);
        sampleAssignment.setType(AssignmentType.DELIVERY);
        sampleAssignment.setStatus(AssignmentStatus.ASSIGNED);
        sampleAssignment.setAssignedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        sampleAssignment.setLastUpdatedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    void publishOrderAssigned_sendsCorrectEvent() throws Exception {
        consumer.subscribe(Collections.singletonList(orderAssignedTopic));

        deliveryKafkaClient.publishOrderAssigned(sampleAssignment);

        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10).toMillis(), 1);
        assertThat(records.count()).isEqualTo(1);
        ConsumerRecord<String, String> record = records.iterator().next();

        assertThat(record.key()).isEqualTo(sampleAssignment.getId().toString());
        Map<String, Object> payload = objectMapper.readValue(record.value(), new TypeReference<>() {});

        assertThat(payload.get("assignmentId")).isEqualTo(sampleAssignment.getId().toString());
        assertThat(payload.get("orderId")).isEqualTo(sampleAssignment.getOrderId().toString());
        assertThat(payload.get("deliveryProfileId")).isEqualTo(sampleAssignment.getDeliveryProfile().getId().toString());
        assertThat(payload.get("deliveryUserId")).isEqualTo(sampleAssignment.getDeliveryProfile().getUser().getId().toString());
        assertThat(payload.get("assignmentType")).isEqualTo(sampleAssignment.getType().toString());
        assertThat(payload.get("status")).isEqualTo(sampleAssignment.getStatus().toString());
        assertThat(payload.get("assignedAt")).isEqualTo(sampleAssignment.getAssignedAt().toString());
        assertThat(payload.get("eventType")).isEqualTo("OrderAssigned");
    }

    @Test
    void publishDeliveryStatusChanged_sendsCorrectEvent() throws Exception {
        consumer.subscribe(Collections.singletonList(deliveryStatusChangedTopic));

        String oldStatus = sampleAssignment.getStatus().toString();
        sampleAssignment.setStatus(AssignmentStatus.EN_ROUTE);
        sampleAssignment.setLastUpdatedAt(Instant.now().plusSeconds(10).truncatedTo(ChronoUnit.MILLIS)); // Simulate update time

        deliveryKafkaClient.publishDeliveryStatusChanged(sampleAssignment, oldStatus);

        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10).toMillis(), 1);
        assertThat(records.count()).isEqualTo(1);
        ConsumerRecord<String, String> record = records.iterator().next();

        assertThat(record.key()).isEqualTo(sampleAssignment.getId().toString());
        Map<String, Object> payload = objectMapper.readValue(record.value(), new TypeReference<>() {});

        assertThat(payload.get("assignmentId")).isEqualTo(sampleAssignment.getId().toString());
        assertThat(payload.get("orderId")).isEqualTo(sampleAssignment.getOrderId().toString());
        assertThat(payload.get("deliveryProfileId")).isEqualTo(sampleAssignment.getDeliveryProfile().getId().toString());
        assertThat(payload.get("newStatus")).isEqualTo(AssignmentStatus.EN_ROUTE.toString());
        assertThat(payload.get("oldStatus")).isEqualTo(oldStatus);
        assertThat(payload.get("statusChangeTimestamp")).isEqualTo(sampleAssignment.getLastUpdatedAt().toString());
        assertThat(payload.get("eventType")).isEqualTo("DeliveryStatusChanged");
    }
}
