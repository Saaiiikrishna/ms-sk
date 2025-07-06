package com.mysillydreams.orderapi.contract;

import com.mysillydreams.orderapi.OrderApiApplication;
import com.mysillydreams.orderapi.dto.avro.LineItem as AvroLineItem;
import com.mysillydreams.orderapi.dto.avro.OrderCreatedEvent as AvroOrderCreatedEvent;
import com.mysillydreams.orderapi.service.KafkaOrderPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.verifier.messaging.boot.AutoConfigureMessageVerifier;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

@SpringBootTest(classes = OrderApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ExtendWith(SpringExtension.class)
@AutoConfigureMessageVerifier // Automatically configures tools for message verification
@ActiveProfiles("test")
// EmbeddedKafka for the publisher to send to. Topics should match contract.
@EmbeddedKafka(partitions = 1, topics = {"order.created", "order.cancelled", "order.created.dlq", "order.cancelled.dlq"})
@DirtiesContext // Ensure context is dirtied after each test class if needed, or per method
public abstract class ContractTestBase {

    @Autowired
    protected KafkaOrderPublisher kafkaOrderPublisher; // 'protected' for generated tests

    // This method will be called by the generated tests to trigger the message publishing.
    // The name of the method should ideally match a convention or be configured in the plugin
    // if it doesn't match `triggeredBy` in the contract (though `triggeredBy` is more for consumer side).
    // For producer tests, the generated test often calls a method in this base class to send a message.
    // Let's make a method that the generated test can call.
    // The contract's `outputMessage` implies this service is the producer.
    // The generated test will call methods here to trigger these outputs.

    public void publishOrderCreatedEvent() {
        // Create a sample AvroOrderCreatedEvent based on the contract's producer values
        AvroLineItem avroLineItem = AvroLineItem.newBuilder()
                .setProductId(UUID.randomUUID().toString())
                .setQuantity(1)
                .setPrice(10.50)
                .build();

        AvroOrderCreatedEvent event = AvroOrderCreatedEvent.newBuilder()
                .setOrderId(UUID.randomUUID().toString())
                .setCustomerId(UUID.randomUUID().toString())
                .setItems(Collections.singletonList(avroLineItem))
                .setTotalAmount(10.50)
                .setCurrency("USD")
                .setCreatedAt(Instant.now().toEpochMilli())
                .build();

        kafkaOrderPublisher.publishOrderCreated(event);
    }

    // Add method for OrderCancelledEvent if there's a contract for it
    // public void publishOrderCancelledEvent() { ... }


    // The spring-cloud-contract-verifier will generate tests in target/generated-test-sources/contracts
    // These tests will extend this base class.
    // We need to configure the plugin in pom.xml to use this base class.
}
