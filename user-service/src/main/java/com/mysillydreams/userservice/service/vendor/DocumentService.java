package com.mysillydreams.userservice.service.vendor;

import com.mysillydreams.userservice.domain.vendor.VendorDocument;
import com.mysillydreams.userservice.domain.vendor.VendorProfile;
import com.mysillydreams.userservice.dto.vendor.PresignedUrlResponse;
import com.mysillydreams.userservice.repository.vendor.VendorDocumentRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import java.util.concurrent.CompletableFuture;

import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;


import java.net.URL;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentService.class);

    private final VendorDocumentRepository documentRepository;
    private final S3Presigner s3Presigner; // Using S3Presigner from AWS SDK v2
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private final String s3BucketName;
    private final String kycDocumentUploadedTopic;

    @Autowired
    public DocumentService(VendorDocumentRepository documentRepository,
                           S3Presigner s3Presigner,
                           KafkaTemplate<String, Object> kafkaTemplate,
                           @Value("${vendor.s3.bucket:mysillydreams-vendor-docs}") String s3BucketName,
                           @Value("${kyc.topic.documentUploaded:kyc.vendor.document.uploaded.v1}") String kycDocumentUploadedTopic) {
        this.documentRepository = documentRepository;
        this.s3Presigner = s3Presigner;
        this.kafkaTemplate = kafkaTemplate;
        this.s3BucketName = s3BucketName;
        this.kycDocumentUploadedTopic = kycDocumentUploadedTopic;
    }

    /**
     * Generates a pre-signed URL for uploading a vendor document to S3.
     * Creates a VendorDocument record in the database before returning the URL.
     *
     * @param vendorProfileId UUID of the vendor profile.
     * @param docType         Type of the document (e.g., "PAN", "GSTIN").
     * @return PresignedUrlResponse containing the URL and S3 key.
     */
    @Transactional
    public PresignedUrlResponse generateUploadUrl(UUID vendorProfileId, String docType) {
        Assert.notNull(vendorProfileId, "Vendor Profile ID cannot be null.");
        Assert.hasText(docType, "Document type cannot be blank.");

        String s3Key = String.format("vendor-docs/%s/%s/%s",
                vendorProfileId.toString(),
                docType.replaceAll("[^a-zA-Z0-9-_.]", "_"), // Sanitize docType for S3 key
                UUID.randomUUID().toString()); // Add randomness to prevent overwrites if same docType uploaded again

        logger.info("Generating pre-signed URL for VendorProfileId: {}, DocType: {}, S3Key: {}, Bucket: {}",
                vendorProfileId, docType, s3Key, s3BucketName);

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(s3BucketName)
                .key(s3Key)
                // .contentType("application/octet-stream") // Or specific content type if known, client should set this
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15)) // URL validity duration
                .putObjectRequest(objectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        URL url = presignedRequest.url();

        // Save document metadata to DB before returning URL
        VendorDocument document = new VendorDocument();
        VendorProfile profileRef = new VendorProfile(); // Create a reference to avoid fetching the full profile
        profileRef.setId(vendorProfileId);
        document.setVendorProfile(profileRef);
        document.setDocType(docType);
        document.setS3Key(s3Key);
        document.setProcessed(false); // Initial state
        // checksum will be updated later via callback
        documentRepository.save(document);
        logger.info("VendorDocument record created with ID: {} for S3Key: {}", document.getId(), s3Key);

        return new PresignedUrlResponse(url.toString(), s3Key);
    }

    /**
     * Handles a callback after a document is uploaded to S3.
     * Updates the VendorDocument record with the checksum and publishes an event.
     *
     * @param s3Key    The S3 key of the uploaded document.
     * @param checksum The checksum (e.g., SHA-256 or ETag from S3) of the uploaded file.
     */
    @Transactional
    public void handleUploadCallback(String s3Key, String checksum) {
        Assert.hasText(s3Key, "S3 key cannot be blank.");
        // Checksum can be optional depending on how it's provided (e.g. S3 ETag might be MD5 for non-multipart)
        // Assert.hasText(checksum, "Checksum cannot be blank.");

        logger.info("Handling S3 upload callback for S3Key: {}, Checksum: {}", s3Key, checksum);

        VendorDocument document = documentRepository.findByS3Key(s3Key)
                .orElseThrow(() -> {
                    logger.error("VendorDocument not found for S3Key: {}", s3Key);
                    return new EntityNotFoundException("Document metadata not found for S3 key: " + s3Key);
                });

        document.setChecksum(checksum);
        // Potentially update other fields, e.g., fileSize if provided by callback
        VendorDocument updatedDocument = documentRepository.save(document);
        logger.info("VendorDocument ID: {} updated with checksum for S3Key: {}", updatedDocument.getId(), s3Key);

        // Publish Kafka event "kyc.document.uploaded"
        Map<String, Object> payload = new HashMap<>();
        payload.put("documentId", updatedDocument.getId().toString());
        payload.put("vendorProfileId", updatedDocument.getVendorProfile().getId().toString());
        payload.put("s3Key", updatedDocument.getS3Key());
        payload.put("docType", updatedDocument.getDocType());
        payload.put("checksum", updatedDocument.getChecksum());
        payload.put("uploadedAt", updatedDocument.getUploadedAt().toString());
        payload.put("eventType", "KycDocumentUploaded");


        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(kycDocumentUploadedTopic, document.getId().toString(), payload);
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                logger.info("Successfully published 'KycDocumentUploaded' event for DocumentId: {}. Topic: {}, Partition: {}, Offset: {}",
                        updatedDocument.getId(),
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                logger.error("Failed to publish 'KycDocumentUploaded' event for DocumentId: {}. Topic: {}",
                        updatedDocument.getId(), kycDocumentUploadedTopic, ex);
            }
        });
    }
}

// Custom exception for DocumentService if needed
class DocumentServiceException extends RuntimeException {
    public DocumentServiceException(String message) {
        super(message);
    }

    public DocumentServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}

// Custom exception for cases where an entity is not found.
// Using jakarta.persistence.EntityNotFoundException is also an option.
class EntityNotFoundException extends RuntimeException {
    public EntityNotFoundException(String message) {
        super(message);
    }
}
