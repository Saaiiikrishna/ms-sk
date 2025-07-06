package com.mysillydreams.vendor.config;

import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

  @Value("${spring.kafka.bootstrap-servers}") // Standard Spring Boot Kafka property
  private String bootstrapServers;

  @Value("${spring.kafka.properties.schema.registry.url}") // Standard Spring Boot Kafka property for SR
  private String schemaRegistryUrl;

  @Value("${spring.kafka.producer.key-serializer}")
  private String keySerializer;

  // Value for value serializer will be KafkaAvroSerializer.class directly

  @Bean
  public ProducerFactory<String, Object> producerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, keySerializer);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
    props.put("schema.registry.url", schemaRegistryUrl); // Confluent specific property for Avro Serializer
    // Add any other producer properties here, e.g., acks, retries, idempotence
    props.put(ProducerConfig.ACKS_CONFIG, "all");
    props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true"); // Recommended for reliable production

    return new DefaultKafkaProducerFactory<>(props);
  }

  @Bean
  public KafkaTemplate<String, Object> kafkaTemplate() {
    return new KafkaTemplate<>(producerFactory());
  }

  // If you plan to consume messages in this service (e.g. for the Outbox Poller to send to Kafka),
  // you might also need ConsumerFactory and KafkaListenerContainerFactory beans.
  // For now, only Producer is configured as per the initial guide.
}
