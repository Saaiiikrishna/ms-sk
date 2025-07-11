package com.mysillydreams.userservice.service.vendor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.userservice.config.UserIntegrationTestBase;
import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.domain.vendor.VendorDocument;
import com.mysillydreams.userservice.domain.vendor.VendorProfile;
import com.mysillydreams.userservice.dto.vendor.PresignedUrlResponse;
import com.mysillydreams.userservice.repository.UserRepository;
import com.mysillydreams.userservice.repository.vendor.VendorDocumentRepository;
import com.mysillydreams.userservice.repository.vendor.VendorProfileRepository;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;


import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE) // No web server needed for service test
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS) // Reset Spring context after class
@EmbeddedKafka(partitions = 1,
               topics = {"${kyc.topic.documentUploaded:kyc.vendor.document.uploaded.v1}"}, // Use property placeholder
               brokerProperties = {"listeners=PLAINTEXT://localhost:9098", "port=9098"}) // Unique port
public class DocumentServiceIntegrationTest extends UserIntegrationTestBase {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private VendorDocumentRepository vendorDocumentRepository;
    @Autowired
    private VendorProfileRepository vendorProfileRepository;
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private S3Client s3Client; // Real S3 client configured to talk to LocalStack

    @Value("${vendor.s3.bucket}") // This should be 'test-bucket' from UserIntegrationTestBase Initializer
    private String testS3BucketName;

    @Value("${kyc.topic.documentUploaded}")
    private String kycDocumentUploadedTopic;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;
    @Autowired
    private ObjectMapper objectMapper; // Spring Boot auto-configures this

    private UserEntity testUser;
    private VendorProfile testVendorProfile;

    @BeforeEach
    void setUpTestData() {
        // Clean up before each test
        vendorDocumentRepository.deleteAll();
        vendorProfileRepository.deleteAll();
        userRepository.deleteAll();

        // Create S3 bucket in LocalStack if it doesn't exist
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(testS3BucketName).build());
            logger.info("Test S3 bucket '{}' created in LocalStack.", testS3BucketName);
        } catch (Exception e) {
            logger.warn("Could not create S3 bucket '{}' (it might already exist): {}", testS3BucketName, e.getMessage());
        }


        testUser = new UserEntity();
        testUser.setReferenceId("doc-serv-user-" + UUID.randomUUID());
        testUser.setEmail(testUser.getReferenceId() + "@example.com");
        testUser = userRepository.saveAndFlush(testUser);

        testVendorProfile = new VendorProfile();
        testVendorProfile.setUser(testUser);
        testVendorProfile.setLegalName("Docs R Us Inc.");
        testVendorProfile = vendorProfileRepository.saveAndFlush(testVendorProfile);
    }

    @AfterEach
    void tearDownData() {
        vendorDocumentRepository.deleteAll();
        vendorProfileRepository.deleteAll();
        userRepository.deleteAll();
    }


    @Test
    void generateUploadUrl_createsDbRecordAndReturnsValidPresignedUrl() throws IOException {
        String docType = "CERTIFICATE_OF_INCORPORATION";

        PresignedUrlResponse response = documentService.generateUploadUrl(testVendorProfile.getId(), docType);

        assertThat(response).isNotNull();
        assertThat(response.getUrl()).isNotNull().startsWith("http://localhost:" + localstack.getMappedPort(LocalStackContainer.Service.S3.getPort()));
        assertThat(response.getKey()).isNotNull().contains(testVendorProfile.getId().toString()).contains(docType);

        // Verify DB record
        Optional<VendorDocument> docOpt = vendorDocumentRepository.findByS3Key(response.getKey());
        assertThat(docOpt).isPresent();
        VendorDocument savedDoc = docOpt.get();
        assertThat(savedDoc.getDocType()).isEqualTo(docType);
        assertThat(savedDoc.getVendorProfile().getId()).isEqualTo(testVendorProfile.getId());
        assertThat(savedDoc.isProcessed()).isFalse();
        assertThat(savedDoc.getChecksum()).isNull();

        // Try to use the presigned URL to upload a dummy file (basic check)
        URL presignedUrl = new URL(response.getUrl());
        HttpURLConnection connection = (HttpURLConnection) presignedUrl.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("PUT");
        // connection.setRequestProperty("Content-Type", "text/plain"); // Client usually sets this
        String fileContent = "This is dummy file content.";
        connection.getOutputStream().write(fileContent.getBytes(StandardCharsets.UTF_8));

        int responseCode = connection.getResponseCode();
        assertThat(responseCode).isEqualTo(HttpURLConnection.HTTP_OK); // 200 OK for successful S3 PUT via presigned URL
        connection.disconnect();

        // Verify object exists in S3 (LocalStack)
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(testS3BucketName).key(response.getKey()).build());
        } catch (NoSuchKeyException e) {
            fail("Object not found in S3 after upload with presigned URL: " + response.getKey());
        }
    }

    @Test
    void handleUploadCallback_updatesDocumentAndPublishesEvent() throws Exception {
        // 1. Setup: Create a document entry as if URL was generated
        String docType = "BANK_STATEMENT";
        String s3Key = String.format("vendor-docs/%s/%s/%s", testVendorProfile.getId(), docType, UUID.randomUUID());
        VendorDocument doc = new VendorDocument();
        doc.setVendorProfile(testVendorProfile);
        doc.setDocType(docType);
        doc.setS3Key(s3Key);
        doc.setProcessed(false);
        vendorDocumentRepository.saveAndFlush(doc);

        String checksum = "dummy-checksum-from-s3-event";

        // 2. Act: Call the service method
        documentService.handleUploadCallback(s3Key, checksum);

        // 3. Assert DB update
        Optional<VendorDocument> updatedDocOpt = vendorDocumentRepository.findByS3Key(s3Key);
        assertThat(updatedDocOpt).isPresent();
        VendorDocument updatedDoc = updatedDocOpt.get();
        assertThat(updatedDoc.getChecksum()).isEqualTo(checksum);
        // processed flag is not changed by this callback, only by orchestrator potentially

        // 4. Assert Kafka event
        try (KafkaConsumer<String, String> consumer = (KafkaConsumer<String, String>) consumerFactory.createConsumer("doc-upload-test-group", null)) {
            consumer.subscribe(Collections.singletonList(kycDocumentUploadedTopic));
            ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10).toMillis(), 1);
            assertThat(records.count()).isEqualTo(1);
            ConsumerRecord<String, String> consumedRecord = records.iterator().next();

            assertThat(consumedRecord.key()).isEqualTo(updatedDoc.getId().toString());
            Map<String, Object> payload = objectMapper.readValue(consumedRecord.value(), new TypeReference<>() {});
            assertThat(payload.get("documentId")).isEqualTo(updatedDoc.getId().toString());
            assertThat(payload.get("vendorProfileId")).isEqualTo(testVendorProfile.getId().toString());
            assertThat(payload.get("s3Key")).isEqualTo(s3Key);
            assertThat(payload.get("docType")).isEqualTo(docType);
            assertThat(payload.get("checksum")).isEqualTo(checksum);
            assertThat(payload.get("eventType")).isEqualTo("KycDocumentUploaded");
        }
    }

    @Test
    void handleUploadCallback_documentNotFound_throwsEntityNotFoundException() {
        String nonExistentS3Key = "non-existent-s3-key-for-callback";
        String checksum = "some-checksum";

        assertThrows(EntityNotFoundException.class, () -> {
            documentService.handleUploadCallback(nonExistentS3Key, checksum);
        });
    }
}
