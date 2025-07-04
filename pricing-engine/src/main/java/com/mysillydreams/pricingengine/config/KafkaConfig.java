package com.mysillydreams.pricingengine.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaStreamsConfiguration;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerde;
import org.apache.kafka.streams.errors.LogAndContinueExceptionHandler; // Added
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler; // Added
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.pricingengine.dto.DynamicPricingRuleDto;
import com.mysillydreams.pricingengine.dto.PriceOverrideDto;
import com.mysillydreams.pricingengine.dto.MetricEvent;
import com.mysillydreams.pricingengine.dto.ItemBasePriceEvent;
import com.mysillydreams.pricingengine.dto.PriceUpdatedEvent; // Added
import org.apache.kafka.common.serialization.Serde;


import java.util.HashMap;
import java.util.Map;

/**
 * Kafka specific configurations for both Listeners and Streams.
 */
@Configuration
@EnableKafkaStreams // Crucial for enabling Kafka Streams
public class KafkaConfig {

    @Value("${kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.application.name}")
    private String applicationName;


    // Configuration for Kafka Streams
    @Bean(name = KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME)
    public KafkaStreamsConfiguration kStreamsConfigs(ObjectMapper objectMapper) { // Inject ObjectMapper for custom Serdes
        Map<String, Object> props = new HashMap<>();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationName + "-streams-app"); // Unique ID for the Streams app
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        // Default value serde for Streams - this will apply if a specific Serde isn't found/configured for a type.
        // We will provide specific JsonSerdes for our DTOs.
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, JsonSerde.class.getName()); // Default if specific not found
        props.put(JsonSerde.TRUSTED_PACKAGES,"com.mysillydreams.pricingengine.dto");


        // Add other important Streams configurations:
        // props.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.EXACTLY_ONCE_V2);
        props.put(StreamsConfig.NUM_STREAM_THREADS_CONFIG, "2");
        props.put(StreamsConfig.REPLICATION_FACTOR_CONFIG, 1); // For local/dev; adjust for prod
        // props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 100);

        // Configure Default Deserialization Exception Handler
        props.put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG, LogAndContinueExceptionHandler.class.getName());

        return new KafkaStreamsConfiguration(props);
    }

    // Bean for StreamsUncaughtExceptionHandler
    @Bean
    public StreamsUncaughtExceptionHandler streamsUncaughtExceptionHandler() {
        // Replace this with a more sophisticated handler in production (e.g., SHUTDOWN_APPLICATION or custom logic)
        return exception -> {
            log.error("CRITICAL: Uncaught exception in Kafka Streams thread [{}], {}, application will shut down.",
                    Thread.currentThread().getName(), exception.getMessage(), exception);
            // For KIP-690, this might be StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.SHUTDOWN_APPLICATION
            // Depending on Spring Kafka version, you might return a specific enum value.
            // For older versions, you might need to call System.exit or similar to force shutdown if that's desired.
            // For now, just logging. A SHUTDOWN_CLIENT or SHUTDOWN_APPLICATION response is typical.
            return StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.REPLACE_THREAD; // Or SHUTDOWN_CLIENT / SHUTDOWN_APPLICATION
        };
    }


    // Standard Kafka Listener Container Factory (used by @KafkaListener unless overridden)
    // This bean is often auto-configured by Spring Boot if not explicitly defined.
    // Defining it here allows for customization if needed.
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        // You can add common configurations here, e.g., error handlers, message converters
        // factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }

    // ConsumerFactory - usually auto-configured by Spring Boot based on application.yml
    // Defining it explicitly if specific global deserialization properties are needed beyond yml.
    @Bean
    public ConsumerFactory<String, Object> consumerFactory(@Value("${kafka.bootstrap-servers}") String bootstrapServers,
                                                           @Value("${kafka.consumer.group-id}") String groupId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.mysillydreams.*"); // Trust DTOs
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        // props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, SomeBaseEvent.class); // If all events have a common base or for specific listeners
        return new DefaultKafkaConsumerFactory<>(props);
    }


    // If you need specific Serdes for Kafka Streams, you can define them as beans

    @Bean
    public Serde<MetricEvent> metricEventSerde(ObjectMapper objectMapper) {
        return new JsonSerde<>(MetricEvent.class, objectMapper);
    }

    @Bean
    public Serde<DynamicPricingRuleDto> dynamicPricingRuleDtoSerde(ObjectMapper objectMapper) {
        // For GlobalKTable, the DTO is used as it's published by the listener.
        return new JsonSerde<>(DynamicPricingRuleDto.class, objectMapper);
    }

    @Bean
    public Serde<PriceOverrideDto> priceOverrideDtoSerde(ObjectMapper objectMapper) {
        // For GlobalKTable, the DTO is used.
        return new JsonSerde<>(PriceOverrideDto.class, objectMapper);
    }

    @Bean
    public Serde<ItemBasePriceEvent> itemBasePriceEventSerde(ObjectMapper objectMapper) {
        return new JsonSerde<>(ItemBasePriceEvent.class, objectMapper);
    }

    @Bean
    public Serde<PriceUpdatedEvent> priceUpdatedEventSerde(ObjectMapper objectMapper) {
        return new JsonSerde<>(PriceUpdatedEvent.class, objectMapper);
    }

    // Serde for UUID keys. The internal topics for rules, overrides, and base prices are keyed by String(UUID).
    // However, the KStream for metrics is rekeyed to UUID. So a UUID Serde is useful.
    @Bean
    public Serde<UUID> uuidSerde() {
        return Serdes.UUID();
    }
}
