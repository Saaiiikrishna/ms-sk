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
    public KafkaStreamsConfiguration kStreamsConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationName + "-streams-app"); // Unique ID for the Streams app
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        // Default value serde for Streams, can be overridden per stream. Using JsonSerde for MetricEvent.
        // Ensure MetricEvent DTO is compatible (no-args constructor, getters/setters)
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, JsonSerde.class.getName());
        // Configure JsonSerde for specific DTOs if needed, or rely on default for MetricEvent
        props.put(JsonSerde.DEFAULT_KEY_TYPE, String.class);
        props.put(JsonSerde.DEFAULT_VALUE_TYPE, com.mysillydreams.pricingengine.dto.MetricEvent.class); // Default type for JsonSerde
        props.put(JsonSerde.TRUSTED_PACKAGES,"com.mysillydreams.pricingengine.dto");


        // Add other important Streams configurations:
        // props.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.EXACTLY_ONCE_V2); // For exactly-once semantics
        props.put(StreamsConfig.NUM_STREAM_THREADS_CONFIG, "2"); // Example: number of stream threads
        props.put(StreamsConfig.REPLICATION_FACTOR_CONFIG, 1); // For local/dev; adjust for prod
        // props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 100); // How often to commit offsets

        return new KafkaStreamsConfiguration(props);
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
    // For example, a JsonSerde for MetricEvent:
    // @Bean
    // public Serde<MetricEvent> metricEventSerde(ObjectMapper objectMapper) {
    //     return new JsonSerde<>(MetricEvent.class, objectMapper);
    // }
}
