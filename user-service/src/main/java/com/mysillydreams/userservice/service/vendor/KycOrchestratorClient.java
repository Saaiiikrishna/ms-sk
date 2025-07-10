package com.mysillydreams.userservice.service.vendor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import java.util.concurrent.CompletableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

import java.util.Map;
import java.util.UUID;

@Service
public class KycOrchestratorClient {

    private static final Logger logger = LoggerFactory.getLogger(KycOrchestratorClient.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String startKycTopic;

    @Autowired
    public KycOrchestratorClient(KafkaTemplate<String, Object> kafkaTemplate,
                                 @Value("${kyc.topic.start:kyc.vendor.start.v1}") String startKycTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.startKycTopic = startKycTopic;
    }

    /**
     * Starts a KYC workflow for the given vendor profile ID by publishing an event to Kafka.
     *
     * @param vendorProfileId The UUID string of the vendor profile.
     * @return The generated workflow ID.
     */
    public String startKycWorkflow(String vendorProfileId) {
        String workflowId = UUID.randomUUID().toString();
        Map<String, String> payload = Map.of(
                "workflowId", workflowId,
                "vendorProfileId", vendorProfileId,
                "eventType", "StartKycVendorWorkflow" // Adding an eventType for clarity
        );

        logger.info("Attempting to start KYC workflow. WorkflowId: {}, VendorProfileId: {}, Topic: {}",
                workflowId, vendorProfileId, startKycTopic);

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(startKycTopic, workflowId, payload); // Using workflowId as message key

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                logger.info("Successfully published 'StartKycVendorWorkflow' event for WorkflowId: {}, VendorProfileId: {}. Topic: {}, Partition: {}, Offset: {}",
                        workflowId,
                        vendorProfileId,
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                logger.error("Failed to publish 'StartKycVendorWorkflow' event for WorkflowId: {}, VendorProfileId: {}. Topic: {}",
                        workflowId, vendorProfileId, startKycTopic, ex);
                // Consider retry mechanisms or specific exception handling if this publish is critical path
                // For now, just logging the error.
            }
        });
        return workflowId;
    }
}
