package com.mysillydreams.userservice.service.delivery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;


import java.io.IOException;
import java.util.UUID;

@Service
public class DeliveryDocumentStorageService {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryDocumentStorageService.class);

    private final S3Client s3Client;
    private final String s3BucketName; // Could be same as vendor or different

    @Autowired
    public DeliveryDocumentStorageService(S3Client s3Client,
                                          @Value("${delivery.s3.photo-bucket:${vendor.s3.bucket}}") String s3BucketName) {
        this.s3Client = s3Client;
        this.s3BucketName = s3BucketName;
        logger.info("DeliveryDocumentStorageService initialized with S3 bucket: {}", this.s3BucketName);
    }

    /**
     * Uploads a delivery-related photo (e.g., proof of delivery) to S3.
     *
     * @param assignmentId The ID of the order assignment this photo relates to.
     * @param deliveryUserId The ID of the delivery user uploading the photo.
     * @param file         The multipart file (photo) to upload.
     * @param docType      A string indicating the type or purpose of the photo (e.g., "PROOF_OF_DELIVERY", "DAMAGED_ITEM").
     * @return The S3 key of the uploaded object.
     * @throws IOException if there's an issue reading the file.
     * @throws RuntimeException if the S3 upload fails.
     */
    public String uploadDeliveryPhoto(UUID assignmentId, UUID deliveryUserId, MultipartFile file, String docType) throws IOException {
        Assert.notNull(assignmentId, "Assignment ID cannot be null.");
        Assert.notNull(deliveryUserId, "Delivery User ID cannot be null.");
        Assert.notNull(file, "File cannot be null.");
        Assert.isTrue(!file.isEmpty(), "File cannot be empty.");
        Assert.hasText(docType, "Document type cannot be blank.");

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
        String extension = "";
        int i = originalFilename.lastIndexOf('.');
        if (i > 0) {
            extension = originalFilename.substring(i); // .jpg, .png
        }

        String s3Key = String.format("delivery-photos/%s/%s/%s_%s%s",
                assignmentId.toString(),
                docType.replaceAll("[^a-zA-Z0-9-_.]", "_"),
                deliveryUserId.toString(), // Include delivery user ID for better organization/auditing
                UUID.randomUUID().toString(), // Ensure unique key
                extension);

        logger.info("Uploading delivery photo to S3. AssignmentId: {}, DocType: {}, S3Key: {}, Bucket: {}, Size: {} bytes",
                assignmentId, docType, s3Key, s3BucketName, file.getSize());

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3BucketName)
                    .key(s3Key)
                    .contentType(file.getContentType()) // Use content type from MultipartFile
                    .contentLength(file.getSize())
                    // .acl(ObjectCannedACL.PRIVATE) // Or your bucket's default
                    .build();

            PutObjectResponse response = s3Client.putObject(putObjectRequest,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            // Check response.eTag() or other indicators of success if needed.
            // SdkClientException will be thrown on failure by putObject.
            logger.info("Delivery photo uploaded successfully to S3. S3Key: {}, ETag: {}", s3Key, response.eTag());
            return s3Key;

        } catch (IOException ioe) {
            logger.error("IOException during S3 upload for S3Key: {}", s3Key, ioe);
            throw ioe; // Re-throw to be handled by controller
        } catch (Exception e) { // Catch SdkClientException or other AWS SDK errors
            logger.error("S3 upload failed for S3Key: {}", s3Key, e);
            throw new RuntimeException("Failed to upload delivery photo to S3.", e);
        }
    }
}
