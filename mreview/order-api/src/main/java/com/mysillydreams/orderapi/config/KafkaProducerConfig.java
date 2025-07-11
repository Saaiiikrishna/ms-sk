package com.mysillydreams.orderapi.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
// import org.springframework.kafka.support.serializer.JsonSerializer; // No longer default
import io.confluent.kafka.serializers.KafkaAvroSerializer; // Avro Serializer

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

  @Value("${kafka.bootstrap-servers}")
  private String bootstrapServers;

  @Value("${kafka.schema-registry-url}") // Added for Avro
  private String schemaRegistryUrl;

  @Bean
  public ProducerFactory<String, Object> producerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    // Configure Avro Serializer for values
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
    props.put("schema.registry.url", schemaRegistryUrl); // Schema Registry URL
    // ensure event ordering & acks
    props.put(ProducerConfig.ACKS_CONFIG, "all");
    // Configure retries
    props.put(ProducerConfig.RETRIES_CONFIG, 3);
    props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000); // 1 second backoff
    // delivery.timeout.ms is also important; it should be higher than retry.backoff.ms * retries + request.timeout.ms
    // props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000); // Default is 120s
    // props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000); // Default is 30s
    return new DefaultKafkaProducerFactory<>(props);
  }

  @Bean
  public KafkaTemplate<String, Object> kafkaTemplate() {
    return new KafkaTemplate<>(producerFactory());
  }
}
