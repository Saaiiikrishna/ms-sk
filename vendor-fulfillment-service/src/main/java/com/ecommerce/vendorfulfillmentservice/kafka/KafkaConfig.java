package com.ecommerce.vendorfulfillmentservice.kafka;

import com.ecommerce.vendorfulfillmentservice.event.OrderReservationSucceededEvent;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer; // Kept for initial placeholder

import java.util.HashMap;
import java.util.Map;

@EnableKafka
@Configuration
public class KafkaConfig { // Renamed from KafkaConsumerConfig

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String defaultGroupId;

    @Value("${spring.kafka.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    // --- Consumer Configuration ---

    private Map<String, Object> baseConsumerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false"); // Manual offset commit
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // Common Schema Registry config for Avro deserializers
        props.put(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
        return props;
    }

    // Configuration for OrderReservationSucceededEvent consumer (currently JSON, placeholder for Avro)
    // IMPORTANT: For actual Avro deserialization of an external event like OrderReservationSucceededEvent,
    // this service would need the Avro schema definition for that event and the generated Java class.
    // The VALUE_DESERIALIZER_CLASS_CONFIG would then be KafkaAvroDeserializer.class.
    @Bean
    public ConsumerFactory<String, Object> orderReservationConsumerFactory() { // Changed to Object for flexibility
        Map<String, Object> props = baseConsumerProps();
        props.put(ConsumerConfig.GROUP_ID_CONFIG, defaultGroupId + ".order-reservation");

        // OPTION 1: Current - JSON Deserializer for placeholder DTO (OrderReservationSucceededEvent)
        // This remains active for now to keep service runnable without external Avro schema/class.
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
        // Ensure fully qualified class name for OrderReservationSucceededEvent (internal DTO)
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, com.ecommerce.vendorfulfillmentservice.event.OrderReservationSucceededEvent.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.ecommerce.vendorfulfillmentservice.event");


        // OPTION 2: AVRO Deserializer (using the placeholder com.ecommerce.ordercore.event.avro.OrderReservationSucceededEventAvro)
        // UNCOMMENT AND USE THIS BLOCK WHEN OrderReservationSucceededEventAvro is a real Avro generated class
        /*
        props.clear(); // Clear previous deserializer props if switching
        props.putAll(baseConsumerProps()); // Re-add base props
        props.put(ConsumerConfig.GROUP_ID_CONFIG, defaultGroupId + ".order-reservation");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
        // props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true); // Already in baseConsumerProps
        // For specific Avro reader, the class specified in the listener must match the one generated from schema.
        // The listener would expect com.ecommerce.ordercore.event.avro.OrderReservationSucceededEventAvro.class
        */

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> orderReservationKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(orderReservationConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        // factory.setCommonErrorHandler(new DefaultErrorHandler(...)); // Consider adding custom error handler

        // If Option 2 (AVRO) is used for ConsumerFactory:
        // The listener method in OrderEventConsumer would need to change its @Payload type to:
        // com.ecommerce.ordercore.event.avro.OrderReservationSucceededEventAvro
        // And the VendorAssignmentService.processOrderReservation would need to accept this Avro type
        // or have a mapper from the Avro type to the internal OrderReservationSucceededEvent DTO.
        return factory;
    }

    // --- Producer Configuration (for Outbox Poller) ---

    private Map<String, Object> baseProducerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class); // For Avro
        props.put(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl); // schema.registry.url for producer
        props.put(ProducerConfig.ACKS_CONFIG, "all"); // For durability
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true); // Ensures ordering and no duplicates from producer side
        return props;
    }

    @Bean
    public ProducerFactory<String, SpecificRecord> avroProducerFactory() {
        // Using SpecificRecord as the value type for KafkaTemplate,
        // as our outbound events (e.g., VendorOrderAssignedEvent) will be Avro SpecificRecords.
        return new DefaultKafkaProducerFactory<>(baseProducerProps());
    }

    @Bean
    public KafkaTemplate<String, SpecificRecord> avroKafkaTemplate() {
        return new KafkaTemplate<>(avroProducerFactory());
    }
}
