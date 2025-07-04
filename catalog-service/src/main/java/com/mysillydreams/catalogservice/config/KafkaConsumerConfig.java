package com.mysillydreams.catalogservice.config;

import com.mysillydreams.catalogservice.kafka.event.CatalogItemEvent;
import com.mysillydreams.catalogservice.kafka.event.CategoryEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String defaultGroupId; // A default group ID, can be overridden in @KafkaListener

    // Generic consumer factory properties
    private Map<String, Object> consumerConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // Value deserializer will be JsonDeserializer, configured with ErrorHandlingDeserializer
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
        // Configure JsonDeserializer specific properties
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.mysillydreams.catalogservice.kafka.event");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, "false"); // If not using type headers from producer
        // props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, Object.class.getName()); // Default if type can't be inferred, but specific listeners are better
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return props;
    }

    // ConsumerFactory for CatalogItemEvent
    @Bean
    public ConsumerFactory<String, CatalogItemEvent> catalogItemEventConsumerFactory() {
        Map<String, Object> props = consumerConfigs();
        // Specific default type for this factory if needed, though @KafkaListener can infer from method signature
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, CatalogItemEvent.class.getName());
        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(new JsonDeserializer<>(CatalogItemEvent.class, false)) // false: ignore type headers
        );
    }

    // ConsumerFactory for CategoryEvent
    @Bean
    public ConsumerFactory<String, CategoryEvent> categoryEventConsumerFactory() {
        Map<String, Object> props = consumerConfigs();
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, CategoryEvent.class.getName());
        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(new JsonDeserializer<>(CategoryEvent.class, false))
        );
    }

    // Generic ConsumerFactory for Object type if any listener needs it (less type-safe)
    // @Bean
    // public ConsumerFactory<String, Object> objectConsumerFactory() {
    //     Map<String, Object> props = consumerConfigs();
    //     props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, Object.class.getName()); // Fallback
    //     return new DefaultKafkaConsumerFactory<>(
    //             props,
    //             new StringDeserializer(),
    //             new ErrorHandlingDeserializer<>(new JsonDeserializer<>(Object.class, false))
    //     );
    // }


    // KafkaListenerContainerFactory, used by @KafkaListener annotations by default if name is "kafkaListenerContainerFactory"
    // Or specify factory name in @KafkaListener(containerFactory = "...")
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CatalogItemEvent> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, CatalogItemEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(catalogItemEventConsumerFactory());
        // factory.setErrorHandler(...); // Add custom error handler if needed
        return factory;
    }

    // Separate factory for CategoryEvent if different configurations are needed, or use the default one if compatible
    // If using the same factory for different event types, ensure the ConsumerFactory is generic enough (e.g. ConsumerFactory<String, Object>)
    // and JsonDeserializer can handle different types, often relying on method signature type inference in @KafkaListener.
    // For type safety, it's often better to have specific factories or ensure the method signature correctly guides deserialization.
    // The current @KafkaListener annotations in CatalogItemIndexerService specify different groupIds,
    // implying they might need different consumer instances, but can share factory config.
    // Let's make the default factory more generic and rely on method signature.
    // Or, provide multiple factories. The current setup uses the default "kafkaListenerContainerFactory" for all.
    // This means it will use the ConsumerFactory<String, CatalogItemEvent>. This will fail for CategoryEvent.
    //
    // Solution: Create a generic factory or multiple specific factories.
    // For now, CatalogItemIndexerService's @KafkaListener annotations will use the default factory "kafkaListenerContainerFactory"
    // which is typed to CatalogItemEvent. This needs correction.
    // I will create a more general factory or specific ones for each listener type.
    // The simplest is to make the default factory use ConsumerFactory<String, Object> and let Spring
    // use the method signature to determine the target type for JsonDeserializer.
    // Let's redefine the default factory to be more generic.

    // Replacing the specific kafkaListenerContainerFactory with a more general one
    // Or, ensure each @KafkaListener specifies its containerFactory that matches its event type.

    // Let's provide specific factories and have listeners use them.
    @Bean("categoryEventKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, CategoryEvent> categoryEventKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, CategoryEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(categoryEventConsumerFactory());
        return factory;
    }

    // The default one can remain for CatalogItemEvent or be renamed.
    // If @KafkaListener does not specify a factory, it looks for "kafkaListenerContainerFactory".
    // So, the existing one is fine for CatalogItemEvent listeners.
}
