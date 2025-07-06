package com.mysillydreams.userservice.service.delivery;

import com.mysillydreams.userservice.config.UserIntegrationTestBase; // Base with LocalStack S3
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class DeliveryDocumentStorageServiceIntegrationTest extends UserIntegrationTestBase {

    @Autowired
    private DeliveryDocumentStorageService deliveryDocumentStorageService;

    @Autowired
    private S3Client s3Client; // Configured by UserIntegrationTestBase to point to LocalStack

    @Value("${vendor.s3.bucket}") // This is 'test-bucket' from UserIntegrationTestBase
    private String s3BucketName;

    // The DeliveryDocumentStorageService uses @Value("${delivery.s3.photo-bucket:${vendor.s3.bucket}}")
    // So it should resolve to 'test-bucket' if 'delivery.s3.photo-bucket' is not set in application-test.yml
    // Let's ensure our UserIntegrationTestBase sets vendor.s3.bucket=test-bucket which it does.

    @BeforeEach
    void setUpS3Bucket() {
        // Ensure the test bucket exists in LocalStack S3
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(s3BucketName).build());
            logger.info("S3 test bucket '{}' created or already exists for test.", s3BucketName);
        } catch (S3Exception e) {
            if (e.awsErrorDetails() != null && "BucketAlreadyOwnedByYou".equals(e.awsErrorDetails().errorCode())) {
                logger.info("S3 test bucket '{}' already exists.", s3BucketName);
            } else {
                logger.error("Failed to create S3 test bucket '{}': {}", s3BucketName, e.getMessage(), e);
                fail("Failed to set up S3 bucket for test: " + e.getMessage());
            }
        }
    }

    @Test
    void uploadDeliveryPhoto_success_uploadsToS3() throws IOException {
        UUID assignmentId = UUID.randomUUID();
        UUID deliveryUserId = UUID.randomUUID();
        String docType = "PROOF_IMAGE";
        String originalFilename = "delivery_proof.jpg";
        String contentType = "image/jpeg";
        byte[] fileContent = "dummy JPEG content".getBytes(StandardCharsets.UTF_8);

        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                originalFilename,
                contentType,
                fileContent
        );

        String s3Key = deliveryDocumentStorageService.uploadDeliveryPhoto(assignmentId, deliveryUserId, multipartFile, docType);

        assertThat(s3Key).isNotNull();
        assertThat(s3Key).startsWith(String.format("delivery-photos/%s/%s/%s_", assignmentId, docType, deliveryUserId));
        assertThat(s3Key).endsWith(".jpg");

        // Verify the object exists in S3 (LocalStack) and has correct metadata (optional, but good)
        try {
            HeadObjectResponse headResponse = s3Client.headObject(
                    HeadObjectRequest.builder().bucket(s3BucketName).key(s3Key).build()
            );
            assertThat(headResponse.contentLength()).isEqualTo(fileContent.length);
            assertThat(headResponse.contentType()).isEqualTo(contentType);
        } catch (NoSuchKeyException e) {
            fail("S3 object not found after upload: " + s3Key);
        } finally {
            // Clean up the created S3 object
            if (s3Key != null) {
                try {
                    s3Client.deleteObject(DeleteObjectRequest.builder().bucket(s3BucketName).key(s3Key).build());
                } catch (Exception e) {
                    logger.warn("Failed to cleanup S3 object {} from bucket {}: {}", s3Key, s3BucketName, e.getMessage());
                }
            }
        }
    }

    @Test
    void uploadDeliveryPhoto_emptyFile_throwsIllegalArgumentException() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.txt", "text/plain", new byte[0]);

        assertThrows(IllegalArgumentException.class, () -> {
            deliveryDocumentStorageService.uploadDeliveryPhoto(UUID.randomUUID(), UUID.randomUUID(), emptyFile, "ANY_TYPE");
        });
    }

    @Test
    void uploadDeliveryPhoto_ioExceptionOnRead_propagatesIOException() throws IOException {
        MultipartFile failingFile = mock(MultipartFile.class);
        when(failingFile.getOriginalFilename()).thenReturn("fail.jpg");
        when(failingFile.getContentType()).thenReturn("image/jpeg");
        when(failingFile.getSize()).thenReturn(100L);
        when(failingFile.isEmpty()).thenReturn(false);
        when(failingFile.getInputStream()).thenThrow(new IOException("Simulated stream read error"));

        assertThrows(IOException.class, () -> {
             deliveryDocumentStorageService.uploadDeliveryPhoto(UUID.randomUUID(), UUID.randomUUID(), failingFile, "IO_FAIL_TYPE");
        });
    }
}
