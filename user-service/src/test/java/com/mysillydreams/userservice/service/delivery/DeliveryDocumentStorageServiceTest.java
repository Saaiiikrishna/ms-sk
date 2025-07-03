package com.mysillydreams.userservice.service.delivery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryDocumentStorageServiceTest {

    @Mock
    private S3Client mockS3Client;

    private String testS3BucketName = "test-delivery-photo-bucket";

    private DeliveryDocumentStorageService deliveryDocumentStorageService;

    @Captor
    private ArgumentCaptor<PutObjectRequest> putObjectRequestCaptor;
    @Captor
    private ArgumentCaptor<RequestBody> requestBodyCaptor;

    private UUID testAssignmentId;
    private UUID testDeliveryUserId;
    private String testDocType = "PROOF_OF_DELIVERY";

    @BeforeEach
    void setUp() {
        deliveryDocumentStorageService = new DeliveryDocumentStorageService(mockS3Client, testS3BucketName);
        testAssignmentId = UUID.randomUUID();
        testDeliveryUserId = UUID.randomUUID();
    }

    private MultipartFile createMockMultipartFile(String originalFilename, String contentType, byte[] content) throws IOException {
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getOriginalFilename()).thenReturn(originalFilename);
        when(mockFile.getContentType()).thenReturn(contentType);
        when(mockFile.getSize()).thenReturn((long) content.length);
        when(mockFile.getBytes()).thenReturn(content);
        when(mockFile.getInputStream()).thenReturn(new ByteArrayInputStream(content));
        when(mockFile.isEmpty()).thenReturn(content.length == 0);
        return mockFile;
    }

    @Test
    void uploadDeliveryPhoto_success() throws IOException {
        byte[] fileContent = "dummy photo content".getBytes();
        MultipartFile mockFile = createMockMultipartFile("photo.jpg", "image/jpeg", fileContent);

        PutObjectResponse mockPutResponse = PutObjectResponse.builder().eTag("test-etag").build();
        when(mockS3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(mockPutResponse);

        String s3Key = deliveryDocumentStorageService.uploadDeliveryPhoto(testAssignmentId, testDeliveryUserId, mockFile, testDocType);

        assertNotNull(s3Key);
        assertTrue(s3Key.startsWith(String.format("delivery-photos/%s/%s/%s_", testAssignmentId, testDocType, testDeliveryUserId)));
        assertTrue(s3Key.endsWith(".jpg"));

        verify(mockS3Client).putObject(putObjectRequestCaptor.capture(), requestBodyCaptor.capture());
        PutObjectRequest capturedRequest = putObjectRequestCaptor.getValue();
        assertEquals(testS3BucketName, capturedRequest.bucket());
        assertEquals(s3Key, capturedRequest.key());
        assertEquals("image/jpeg", capturedRequest.contentType());
        assertEquals(fileContent.length, capturedRequest.contentLength());

        // Verify RequestBody content (more complex, can check length or if input stream was used)
        assertNotNull(requestBodyCaptor.getValue());
    }

    @Test
    void uploadDeliveryPhoto_fileNameWithoutExtension_handlesCorrectly() throws IOException {
        MultipartFile mockFile = createMockMultipartFile("photo_no_ext", "image/png", "content".getBytes());
        when(mockS3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().eTag("etag").build());

        String s3Key = deliveryDocumentStorageService.uploadDeliveryPhoto(testAssignmentId, testDeliveryUserId, mockFile, "OTHER_TYPE");
        assertFalse(s3Key.endsWith(".")); // Should not just end with a dot if no extension
        assertTrue(s3Key.contains(testAssignmentId.toString()));
    }


    @Test
    void uploadDeliveryPhoto_emptyFile_throwsIllegalArgumentException() throws IOException {
        MultipartFile mockFile = createMockMultipartFile("empty.txt", "text/plain", new byte[0]);
        // Mockito default for isEmpty is false, so explicitly set it if needed, but Assert.isTrue(!file.isEmpty()) checks it.
        // when(mockFile.isEmpty()).thenReturn(true); // Already handled by createMockMultipartFile if content length is 0

        assertThrows(IllegalArgumentException.class, () -> {
            deliveryDocumentStorageService.uploadDeliveryPhoto(testAssignmentId, testDeliveryUserId, mockFile, testDocType);
        });
    }

    @Test
    void uploadDeliveryPhoto_s3ClientThrowsException_throwsRuntimeException() throws IOException {
        MultipartFile mockFile = createMockMultipartFile("photo.png", "image/png", "content".getBytes());
        when(mockS3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(software.amazon.awssdk.core.exception.SdkClientException.builder().message("S3 Error").build());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            deliveryDocumentStorageService.uploadDeliveryPhoto(testAssignmentId, testDeliveryUserId, mockFile, testDocType);
        });
        assertTrue(ex.getMessage().contains("Failed to upload delivery photo to S3"));
    }

    @Test
    void uploadDeliveryPhoto_fileGetInputStreamThrowsIOException_propagatesIOException() throws IOException {
        MultipartFile mockFile = createMockMultipartFile("photo.gif", "image/gif", "content".getBytes());
        when(mockFile.getInputStream()).thenThrow(new IOException("Failed to read stream"));

        assertThrows(IOException.class, () -> {
            deliveryDocumentStorageService.uploadDeliveryPhoto(testAssignmentId, testDeliveryUserId, mockFile, testDocType);
        });
    }
}
