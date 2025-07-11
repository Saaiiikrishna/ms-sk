package com.mysillydreams.userservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    private static final Logger logger = LoggerFactory.getLogger(KafkaConfig.class);

    @Value("${kyc.topic.start:kyc.vendor.start.v1}")
    private String startKycTopicName;

    @Value("${kyc.topic.documentUploaded:kyc.vendor.document.uploaded.v1}")
    private String kycDocumentUploadedTopicName;

    @Value("${inventory.topic.itemCreated:inventory.item.created.v1}")
    private String inventoryItemCreatedTopicName;

    @Value("${inventory.topic.stockAdjusted:inventory.stock.adjusted.v1}")
    private String inventoryStockAdjustedTopicName;

    @Value("${delivery.topic.orderAssigned:order.assigned.v1}")
    private String deliveryOrderAssignedTopicName;

    @Value("${delivery.topic.deliveryStatusChanged:delivery.status.changed.v1}")
    private String deliveryStatusChangedTopicName;

    @Value("${support.topic.ticketCreated:support.ticket.created.v1}")
    private String supportTicketCreatedTopicName;

    @Value("${support.topic.ticketUpdated:support.ticket.updated.v1}")
    private String supportTicketUpdatedTopicName;

    @Value("${user.topic.archived:user.archived.v1}")
    private String userArchivedTopicName;

    // Default number of partitions and replicas for auto-created topics
    // These should be configured based on production needs if topic creation is enabled.
    private static final int DEFAULT_PARTITIONS = 3;
    private static final short DEFAULT_REPLICAS = 1; // For local/dev; in prod, should be >= 2 (e.g., 3)

    @Bean
    public NewTopic startKycTopic() {
        logger.info("Declaring Kafka topic bean: {}", startKycTopicName);
        return TopicBuilder.name(startKycTopicName)
                .partitions(DEFAULT_PARTITIONS)
                .replicas(DEFAULT_REPLICAS)
                .compact() // Example: if this topic could benefit from compaction
                .build();
    }

    @Bean
    public NewTopic kycDocumentUploadedTopic() {
        logger.info("Declaring Kafka topic bean: {}", kycDocumentUploadedTopicName);
        return TopicBuilder.name(kycDocumentUploadedTopicName)
                .partitions(DEFAULT_PARTITIONS)
                .replicas(DEFAULT_REPLICAS)
                // .config(TopicConfig.RETENTION_MS_CONFIG, "" + Duration.ofDays(7).toMillis()) // Example: set retention
                .build();
    }

    @Bean
    public NewTopic inventoryItemCreatedTopic() {
        logger.info("Declaring Kafka topic bean: {}", inventoryItemCreatedTopicName);
        return TopicBuilder.name(inventoryItemCreatedTopicName)
                .partitions(DEFAULT_PARTITIONS)
                .replicas(DEFAULT_REPLICAS)
                .build();
    }

    @Bean
    public NewTopic inventoryStockAdjustedTopic() {
        logger.info("Declaring Kafka topic bean: {}", inventoryStockAdjustedTopicName);
        return TopicBuilder.name(inventoryStockAdjustedTopicName)
                .partitions(DEFAULT_PARTITIONS)
                .replicas(DEFAULT_REPLICAS)
                .build();
    }

    @Bean
    public NewTopic deliveryOrderAssignedTopic() {
        logger.info("Declaring Kafka topic bean: {}", deliveryOrderAssignedTopicName);
        return TopicBuilder.name(deliveryOrderAssignedTopicName)
                .partitions(DEFAULT_PARTITIONS)
                .replicas(DEFAULT_REPLICAS)
                .build();
    }

    @Bean
    public NewTopic deliveryStatusChangedTopic() {
        logger.info("Declaring Kafka topic bean: {}", deliveryStatusChangedTopicName);
        return TopicBuilder.name(deliveryStatusChangedTopicName)
                .partitions(DEFAULT_PARTITIONS)
                .replicas(DEFAULT_REPLICAS)
                .build();
    }

    @Bean
    public NewTopic supportTicketCreatedTopic() {
        logger.info("Declaring Kafka topic bean: {}", supportTicketCreatedTopicName);
        return TopicBuilder.name(supportTicketCreatedTopicName)
                .partitions(DEFAULT_PARTITIONS)
                .replicas(DEFAULT_REPLICAS)
                .build();
    }

    @Bean
    public NewTopic supportTicketUpdatedTopic() {
        logger.info("Declaring Kafka topic bean: {}", supportTicketUpdatedTopicName);
        return TopicBuilder.name(supportTicketUpdatedTopicName)
                .partitions(DEFAULT_PARTITIONS)
                .replicas(DEFAULT_REPLICAS)
                .build();
    }

    // TODO: Add NewTopic beans for user.created and user.updated if UserService will publish these.
    /*
    @Value("${user.topic.created:user.created.v1}")
    private String userCreatedTopicName;

    @Value("${user.topic.updated:user.updated.v1}")
    private String userUpdatedTopicName;

    @Bean
    public NewTopic userCreatedTopic() {
        logger.info("Declaring Kafka topic bean: {}", userCreatedTopicName);
        return TopicBuilder.name(userCreatedTopicName)
                .partitions(DEFAULT_PARTITIONS)
                .replicas(DEFAULT_REPLICAS)
                .build();
    }

    @Bean
    public NewTopic userUpdatedTopic() {
        logger.info("Declaring Kafka topic bean: {}", userUpdatedTopicName);
        return TopicBuilder.name(userUpdatedTopicName)
                .partitions(DEFAULT_PARTITIONS)
                .replicas(DEFAULT_REPLICAS)
                .build();
    }
    */

    // Note: For these beans to attempt topic creation, the Kafka broker needs to allow
    // auto topic creation, or the Kafka admin client used by Spring Kafka needs
    // appropriate permissions. In many production environments, auto topic creation is disabled,
    // and topics are provisioned by administrators or IaC tools.
    // Spring Boot's `spring.kafka.admin.auto-create=true` (default) enables this behavior.
}
