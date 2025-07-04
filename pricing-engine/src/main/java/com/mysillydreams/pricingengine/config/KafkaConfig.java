package com.mysillydreams.pricingengine.config;

import org.springframework.context.annotation.Configuration;

/**
 * Kafka specific configurations.
 * Beans for Kafka consumers/producers can be defined here if needed,
 * though much can be configured via application.yml.
 */
@Configuration
public class KafkaConfig {
    // Example: Define KafkaListenerContainerFactory if specific customizations are needed
    // that cannot be achieved through application.yml properties.

    // @Bean
    // public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
    // ConsumerFactory<String, String> consumerFactory) {
    //     ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
    //     factory.setConsumerFactory(consumerFactory);
    // Additional configurations like error handlers, filtering, etc.
    // return factory;
    // }
}
