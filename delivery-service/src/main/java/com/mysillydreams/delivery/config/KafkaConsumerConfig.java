package com.mysillydreams.delivery.config;

import com.mysillydreams.delivery.dto.ShipmentRequestedDto; // Assuming this DTO will be created
// import com.mysillydreams.delivery.dto.ShipmentRequestedDto; // Will use Avro generated class

// Using Avro deserializer
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
// import org.springframework.kafka.support.serializer.JsonDeserializer; // Replaced by Avro
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${kafka.consumer.group-id:delivery-service-group}")
    private String groupId;

    @Value("${kafka.consumer.auto-offset-reset:earliest}")
    private String autoOffsetReset;

    @Value("${kafka.schema-registry-url}") // For Avro
    private String schemaRegistryUrl;

    // This factory is now generic for Avro SpecificRecord consumption
    @Bean
    public ConsumerFactory<String, Object> avroConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        // Value Deserializer for Avro SpecificRecords, wrapped in ErrorHandlingDeserializer
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, KafkaAvroDeserializer.class);
        // KafkaAvroDeserializer specific properties
        props.put(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, "true");
        // If specific Avro classes are not in standard classpath locations or for more complex scenarios,
        // you might need to provide properties for type mapping or use GenericRecord.
        // For SPECIFIC_AVRO_READER_CONFIG=true, it relies on the class being available.

        return new DefaultKafkaConsumerFactory<>(props);
    }

    // This listener container factory is now generic for Avro SpecificRecords
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactoryAvro() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(avroConsumerFactory()); // Use the Avro consumer factory

        // Common Error Handler
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            new FixedBackOff(2000L, 3L) // 2s interval, 3 retries
        );
        errorHandler.setCommitRecovered(true);
        // Add specific exceptions for retry or non-retry if needed
        // e.g., errorHandler.addNotRetryableExceptions(NonRecoverableDeserializationException.class);
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }

    // If other listeners consume different types (e.g., JsonNode, other Avro types),
    // create additional ConsumerFactory and ListenerContainerFactory beans for them.
}
