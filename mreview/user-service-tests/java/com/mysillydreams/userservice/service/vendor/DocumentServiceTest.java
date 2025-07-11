package com.mysillydreams.userservice.service.vendor;

import com.mysillydreams.userservice.domain.vendor.VendorDocument;
import com.mysillydreams.userservice.domain.vendor.VendorProfile;
import com.mysillydreams.userservice.dto.vendor.PresignedUrlResponse;
import com.mysillydreams.userservice.repository.vendor.VendorDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.concurrent.SettableListenableFuture;


import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private VendorDocumentRepository mockDocumentRepository;
    @Mock
    private S3Presigner mockS3Presigner;
    @Mock
    private KafkaTemplate<String, Object> mockKafkaTemplate;

    private String testS3BucketName = "test-vendor-bucket";
    private String testKycDocUploadedTopic = "test.kyc.doc.uploaded";

    // Manually instantiate as some constructor args are @Value injected
    private DocumentService documentService;

    @Captor
    private ArgumentCaptor<VendorDocument> vendorDocumentCaptor;
    @Captor
    private ArgumentCaptor<PutObjectPresignRequest> presignRequestCaptor;
    @Captor
    private ArgumentCaptor<Map<String, Object>> kafkaPayloadCaptor;


    @BeforeEach
    void setUp() {
        documentService = new DocumentService(
                mockDocumentRepository,
                mockS3Presigner,
                mockKafkaTemplate,
                testS3BucketName,
                testKycDocUploadedTopic
        );
    }

    @Test
    void generateUploadUrl_success() throws MalformedURLException {
        // Arrange
        UUID vendorProfileId = UUID.randomUUID();
        String docType = "PAN_CARD";
        String expectedS3KeyPattern = String.format("vendor-docs/%s/%s/", vendorProfileId, docType);
        URL mockPresignedUrl = new URL("https://s3.presigned.url/test-key");

        PresignedPutObjectRequest mockPresignedPutReq = mock(PresignedPutObjectRequest.class);
        when(mockPresignedPutReq.url()).thenReturn(mockPresignedUrl);
        when(mockS3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(mockPresignedPutReq);
        when(mockDocumentRepository.save(any(VendorDocument.class))).thenAnswer(inv -> inv.getArgument(0));


        // Act
        PresignedUrlResponse response = documentService.generateUploadUrl(vendorProfileId, docType);

        // Assert
        assertNotNull(response);
        assertEquals(mockPresignedUrl.toString(), response.getUrl());
        assertNotNull(response.getKey());
        assertTrue(response.getKey().startsWith(expectedS3KeyPattern));

        verify(mockS3Presigner).presignPutObject(presignRequestCaptor.capture());
        PutObjectPresignRequest capturedPresignRequest = presignRequestCaptor.getValue();
        assertEquals(Duration.ofMinutes(15), capturedPresignRequest.signatureDuration());
        assertEquals(testS3BucketName, capturedPresignRequest.putObjectRequest().bucket());
        assertEquals(response.getKey(), capturedPresignRequest.putObjectRequest().key());


        verify(mockDocumentRepository).save(vendorDocumentCaptor.capture());
        VendorDocument savedDocument = vendorDocumentCaptor.getValue();
        assertEquals(vendorProfileId, savedDocument.getVendorProfile().getId());
        assertEquals(docType, savedDocument.getDocType());
        assertEquals(response.getKey(), savedDocument.getS3Key());
        assertFalse(savedDocument.isProcessed());
    }

    @Test
    void generateUploadUrl_nullProfileId_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> {
            documentService.generateUploadUrl(null, "PAN");
        });
    }

    @Test
    void generateUploadUrl_blankDocType_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> {
            documentService.generateUploadUrl(UUID.randomUUID(), " ");
        });
    }


    @Test
    void handleUploadCallback_success() {
        // Arrange
        String s3Key = "vendor-docs/some-profile-id/PAN/some-uuid";
        String checksum = "test-checksum-sha256";
        UUID docId = UUID.randomUUID();
        UUID vendorProfileId = UUID.randomUUID();

        VendorDocument existingDocument = new VendorDocument();
        existingDocument.setId(docId);
        existingDocument.setS3Key(s3Key);
        existingDocument.setDocType("PAN");
        VendorProfile profileRef = new VendorProfile(vendorProfileId);
        existingDocument.setVendorProfile(profileRef);
        existingDocument.setUploadedAt(Instant.now().minusSeconds(60)); // Set an upload time

        when(mockDocumentRepository.findByS3Key(s3Key)).thenReturn(Optional.of(existingDocument));
        when(mockDocumentRepository.save(any(VendorDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        SettableListenableFuture<SendResult<String, Object>> future = new SettableListenableFuture<>();
        when(mockKafkaTemplate.send(eq(testKycDocUploadedTopic), eq(docId.toString()), anyMap())).thenReturn(future);


        // Act
        documentService.handleUploadCallback(s3Key, checksum);

        // Assert
        verify(mockDocumentRepository).save(vendorDocumentCaptor.capture());
        VendorDocument updatedDocument = vendorDocumentCaptor.getValue();
        assertEquals(checksum, updatedDocument.getChecksum());

        verify(mockKafkaTemplate).send(eq(testKycDocUploadedTopic), eq(docId.toString()), kafkaPayloadCaptor.capture());
        Map<String, Object> capturedPayload = kafkaPayloadCaptor.getValue();
        assertEquals(docId.toString(), capturedPayload.get("documentId"));
        assertEquals(vendorProfileId.toString(), capturedPayload.get("vendorProfileId"));
        assertEquals(s3Key, capturedPayload.get("s3Key"));
        assertEquals("PAN", capturedPayload.get("docType"));
        assertEquals(checksum, capturedPayload.get("checksum"));
        assertEquals("KycDocumentUploaded", capturedPayload.get("eventType"));
        assertNotNull(capturedPayload.get("uploadedAt"));
    }

    @Test
    void handleUploadCallback_documentNotFound_throwsEntityNotFoundException() {
        String nonExistentS3Key = "non-existent-key";
        when(mockDocumentRepository.findByS3Key(nonExistentS3Key)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> {
            documentService.handleUploadCallback(nonExistentS3Key, "checksum");
        });
        verify(mockDocumentRepository, never()).save(any());
        verifyNoInteractions(mockKafkaTemplate);
    }
}
