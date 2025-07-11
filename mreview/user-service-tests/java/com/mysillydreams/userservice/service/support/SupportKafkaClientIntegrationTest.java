package com.mysillydreams.userservice.service.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.userservice.domain.support.SupportProfile;
import com.mysillydreams.userservice.domain.support.SupportTicket;
import com.mysillydreams.userservice.domain.support.TicketStatus;

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
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EmbeddedKafka(partitions = 1,
               topics = {
                    "${support.topic.ticketCreated:support.ticket.created.v1}",
                    "${support.topic.ticketUpdated:support.ticket.updated.v1}"
               },
               brokerProperties = {"listeners=PLAINTEXT://localhost:9104", "port=9104"}) // Unique port
public class SupportKafkaClientIntegrationTest {

    @Autowired
    private SupportKafkaClient supportKafkaClient;

    @Value("${support.topic.ticketCreated}")
    private String ticketCreatedTopic;
    @Value("${support.topic.ticketUpdated}")
    private String ticketUpdatedTopic;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;
    @Autowired
    private ObjectMapper objectMapper;

    private KafkaConsumer<String, String> consumer;
    private SupportTicket sampleTicket;

    @BeforeEach
    void setUp() {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("support-kafka-test-group", "true", EmbeddedKafkaBroker.BROKER_ADDRESS_PLACEHOLDER);
        consumerProps.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        consumer = (KafkaConsumer<String, String>) consumerFactory.createConsumer("support-kafka-test-group", null, null, consumerProps);

        SupportProfile assignedTo = new SupportProfile();
        assignedTo.setId(UUID.randomUUID());

        sampleTicket = new SupportTicket();
        sampleTicket.setId(UUID.randomUUID());
        sampleTicket.setCustomerId(UUID.randomUUID());
        sampleTicket.setSubject("Integration Test Ticket");
        sampleTicket.setStatus(TicketStatus.OPEN);
        sampleTicket.setCreatedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        sampleTicket.setUpdatedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        sampleTicket.setAssignedTo(assignedTo);
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    void publishSupportTicketCreated_sendsCorrectEvent() throws Exception {
        consumer.subscribe(Collections.singletonList(ticketCreatedTopic));

        supportKafkaClient.publishSupportTicketCreated(sampleTicket);

        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10).toMillis(), 1);
        assertThat(records.count()).isEqualTo(1);
        ConsumerRecord<String, String> record = records.iterator().next();

        assertThat(record.key()).isEqualTo(sampleTicket.getId().toString());
        Map<String, Object> payload = objectMapper.readValue(record.value(), new TypeReference<>() {});

        assertThat(payload.get("ticketId")).isEqualTo(sampleTicket.getId().toString());
        assertThat(payload.get("customerId")).isEqualTo(sampleTicket.getCustomerId().toString());
        assertThat(payload.get("subject")).isEqualTo(sampleTicket.getSubject());
        assertThat(payload.get("status")).isEqualTo(sampleTicket.getStatus().toString());
        assertThat(payload.get("createdAt")).isEqualTo(sampleTicket.getCreatedAt().toString());
        assertThat(payload.get("assignedToSupportProfileId")).isEqualTo(sampleTicket.getAssignedTo().getId().toString());
        assertThat(payload.get("eventType")).isEqualTo("SupportTicketCreated");
    }

    @Test
    void publishSupportTicketUpdated_sendsCorrectEvent() throws Exception {
        consumer.subscribe(Collections.singletonList(ticketUpdatedTopic));

        String oldStatus = sampleTicket.getStatus().toString();
        sampleTicket.setStatus(TicketStatus.IN_PROGRESS);
        sampleTicket.setUpdatedAt(Instant.now().plusSeconds(60).truncatedTo(ChronoUnit.MILLIS));
        UUID newMessageId = UUID.randomUUID();

        supportKafkaClient.publishSupportTicketUpdated(sampleTicket, oldStatus, newMessageId);

        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10).toMillis(), 1);
        assertThat(records.count()).isEqualTo(1);
        ConsumerRecord<String, String> record = records.iterator().next();

        assertThat(record.key()).isEqualTo(sampleTicket.getId().toString());
        Map<String, Object> payload = objectMapper.readValue(record.value(), new TypeReference<>() {});

        assertThat(payload.get("ticketId")).isEqualTo(sampleTicket.getId().toString());
        assertThat(payload.get("customerId")).isEqualTo(sampleTicket.getCustomerId().toString());
        assertThat(payload.get("newStatus")).isEqualTo(TicketStatus.IN_PROGRESS.toString());
        assertThat(payload.get("oldStatus")).isEqualTo(oldStatus);
        assertThat(payload.get("assignedToSupportProfileId")).isEqualTo(sampleTicket.getAssignedTo().getId().toString());
        assertThat(payload.get("newMessageId")).isEqualTo(newMessageId.toString());
        assertThat(payload.get("updatedAt")).isEqualTo(sampleTicket.getUpdatedAt().toString());
        assertThat(payload.get("eventType")).isEqualTo("SupportTicketUpdated");
    }
}
