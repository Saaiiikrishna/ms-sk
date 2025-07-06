package com.mysillydreams.payment.config;

// Assuming Avro DTO is in this package after generation
import com.mysillydreams.payment.dto.PaymentRequestedEvent;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
// Corrected import for KafkaAvroSerializerConfig if it's a separate class/constant holder
// For recent Confluent versions, this might be part of KafkaAvroSerializer itself or AbstractKafkaAvroSerDeConfig
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;


import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${kafka.consumer.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    @Value("${kafka.consumer.group-id}")
    private String consumerGroupId;

    // Consumer Factory for PaymentRequestedEvent
    @Bean
    public ConsumerFactory<String, PaymentRequestedEvent> paymentRequestedConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
        // props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"); // Set in application.yml if needed
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // Explicitly set from application.yml
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentRequestedEvent> paymentRequestedKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, PaymentRequestedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(paymentRequestedConsumerFactory());
        factory.getContainerProperties().setAckMode(ConcurrentKafkaListenerContainerFactory.AckMode.MANUAL_IMMEDIATE); // Ensure manual ack
        return factory;
    }

    // Producer Factory for generic Avro records (Object) for outbox events
    @Bean
    public ProducerFactory<String, Object> avroProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        // props.put(ProducerConfig.ACKS_CONFIG, "all"); // Set in application.yml if needed
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> avroKafkaTemplate() {
        return new KafkaTemplate<>(avroProducerFactory());
    }
}
