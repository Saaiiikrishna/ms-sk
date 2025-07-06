package com.mysillydreams.ordercore.config;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
// import org.springframework.kafka.support.serializer.JsonSerializer; // Replaced by Avro
import io.confluent.kafka.serializers.KafkaAvroSerializer;


import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Value("${kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${kafka.schema-registry-url}")
    private String schemaRegistryUrl;

    @Bean
    public ProducerFactory<String, Object> producerFactoryAvro() { // Generic for Avro specific records
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        props.put("schema.registry.url", schemaRegistryUrl);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        // Auto register schemas for dev/test, can be false in prod for control
        props.put("auto.register.schemas", true);


        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplateAvro() { // Generic for Avro specific records
        return new KafkaTemplate<>(producerFactoryAvro());
    }

    // Note: The OutboxPoller was using KafkaTemplate<String, JsonNode>.
    // If OutboxEvent stores payload as JsonNode but events are published as Avro,
    // the poller will need to deserialize JsonNode to the specific Avro type first,
    // then use the kafkaTemplateAvro().
    // Alternatively, OutboxEventService could store Avro binary in OutboxEvent.payload if it's a byte[] field,
    // or store the Avro object itself if Redis/DB serializer for OutboxEvent.payload can handle it (less likely for JSONB).
    // For now, OutboxPoller will need adjustment to use this Avro template and handle payload conversion.
}
