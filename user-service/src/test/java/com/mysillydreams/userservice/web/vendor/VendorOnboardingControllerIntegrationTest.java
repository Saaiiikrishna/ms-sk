package com.mysillydreams.userservice.web.vendor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.userservice.config.UserIntegrationTestBase;
import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.domain.vendor.VendorDocument;
import com.mysillydreams.userservice.domain.vendor.VendorProfile;
import com.mysillydreams.userservice.domain.vendor.VendorStatus;
import com.mysillydreams.userservice.dto.vendor.RegisterVendorRequest;
import com.mysillydreams.userservice.dto.vendor.VendorProfileDto;
import com.mysillydreams.userservice.repository.UserRepository;
import com.mysillydreams.userservice.repository.vendor.VendorDocumentRepository;
import com.mysillydreams.userservice.repository.vendor.VendorProfileRepository;
import com.mysillydreams.userservice.service.UserService;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;


import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EmbeddedKafka(partitions = 1,
               topics = {"${kyc.topic.start:kyc.vendor.start.v1}", "${kyc.topic.documentUploaded:kyc.vendor.document.uploaded.v1}"},
               brokerProperties = {"listeners=PLAINTEXT://localhost:9099", "port=9099"}) // Yet another port
public class VendorOnboardingControllerIntegrationTest extends UserIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private VendorProfileRepository vendorProfileRepository;
    @Autowired
    private VendorDocumentRepository vendorDocumentRepository;
    @Autowired
    private UserService userService; // Real service
    @Autowired
    private S3Client s3Client;
    @Value("${vendor.s3.bucket}")
    private String testS3BucketName;
    @Value("${kyc.topic.start}")
    private String kycStartTopic;
    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    private UserEntity testUser;
    private UUID testUserId;

    @BeforeEach
    void setUpBaseUserAndBucket() {
        // Clean up
        vendorDocumentRepository.deleteAllInBatch();
        vendorProfileRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        // Create S3 bucket in LocalStack
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(testS3BucketName).build());
        } catch (Exception e) {
            // Ignore if bucket already exists
        }

        testUser = new UserEntity();
        testUser.setReferenceId("vendor-controller-user-" + UUID.randomUUID());
        testUser.setEmail(testUser.getReferenceId() + "@example.com");
        testUser.setName("Vendor User For Controller Test");
        testUser = userRepository.saveAndFlush(testUser);
        testUserId = testUser.getId();
    }

    @AfterEach
    void tearDownData() {
        vendorDocumentRepository.deleteAllInBatch();
        vendorProfileRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }


    @Test
    void registerVendor_success_createsProfileAndStartsKycWorkflow() throws Exception {
        RegisterVendorRequest request = new RegisterVendorRequest();
        request.setLegalName("Legally Sound Vendors LLC");

        MvcResult result = mockMvc.perform(post("/vendor-onboarding/register")
                        .header("X-User-Id", testUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.legalName", is(request.getLegalName())))
                .andExpect(jsonPath("$.status", is(VendorStatus.KYC_IN_PROGRESS.toString())))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.kycWorkflowId", notNullValue()))
                .andReturn();

        VendorProfileDto responseDto = objectMapper.readValue(result.getResponse().getContentAsString(), VendorProfileDto.class);

        // Verify DB state
        Optional<UserEntity> updatedUserOpt = userRepository.findById(testUserId);
        assertThat(updatedUserOpt).isPresent();
        assertThat(updatedUserOpt.get().getRoles()).contains("ROLE_VENDOR_USER");

        Optional<VendorProfile> profileOpt = vendorProfileRepository.findById(responseDto.getId());
        assertThat(profileOpt).isPresent();
        VendorProfile profile = profileOpt.get();
        assertThat(profile.getUser().getId()).isEqualTo(testUserId);
        assertThat(profile.getLegalName()).isEqualTo(request.getLegalName());
        assertThat(profile.getStatus()).isEqualTo(VendorStatus.KYC_IN_PROGRESS);
        assertThat(profile.getKycWorkflowId()).isEqualTo(responseDto.getKycWorkflowId());

        // Verify Kafka event for KYC start
        try (KafkaConsumer<String, String> consumer = (KafkaConsumer<String, String>) consumerFactory.createConsumer("kyc-start-test-group", null)) {
            consumer.subscribe(Collections.singletonList(kycStartTopic));
            ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10).toMillis(), 1);
            assertThat(records.count()).isEqualTo(1);
            ConsumerRecord<String, String> consumedRecord = records.iterator().next();

            assertThat(consumedRecord.key()).isEqualTo(profile.getKycWorkflowId());
            Map<String, Object> payload = objectMapper.readValue(consumedRecord.value(), new TypeReference<>() {});
            assertThat(payload.get("workflowId")).isEqualTo(profile.getKycWorkflowId());
            assertThat(payload.get("vendorProfileId")).isEqualTo(profile.getId().toString());
            assertThat(payload.get("eventType")).isEqualTo("StartKycVendorWorkflow");
        }
    }

    @Test
    void getVendorProfile_whenExists_returnsProfile() throws Exception {
        // Setup: Register a vendor first
        VendorProfile profile = new VendorProfile();
        profile.setUser(testUser);
        profile.setLegalName("My Test Vendor Co");
        profile.setStatus(VendorStatus.ACTIVE);
        profile = vendorProfileRepository.saveAndFlush(profile);

        mockMvc.perform(get("/vendor-onboarding/profile")
                        .header("X-User-Id", testUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(profile.getId().toString())))
                .andExpect(jsonPath("$.legalName", is("My Test Vendor Co")))
                .andExpect(jsonPath("$.status", is(VendorStatus.ACTIVE.toString())));
    }

    @Test
    void getVendorProfile_whenNotExists_returnsNotFound() throws Exception {
        mockMvc.perform(get("/vendor-onboarding/profile")
                        .header("X-User-Id", testUserId.toString())) // This user has no profile yet
                .andExpect(status().isNotFound());
    }

    @Test
    void generateDocumentUploadUrl_success() throws Exception {
        // Setup: Register a vendor first
        VendorProfile profile = new VendorProfile();
        profile.setUser(testUser);
        profile.setLegalName("Doc Upload Vendor");
        profile = vendorProfileRepository.saveAndFlush(profile);

        String docType = "TAX_ID";

        mockMvc.perform(post("/vendor-onboarding/documents/upload-url")
                        .header("X-User-Id", testUserId.toString())
                        .param("docType", docType))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", notNullValue()))
                .andExpect(jsonPath("$.key", notNullValue()))
                .andExpect(jsonPath("$.key", org.hamcrest.Matchers.containsString(profile.getId().toString())))
                .andExpect(jsonPath("$.key", org.hamcrest.Matchers.containsString(docType)));

        // Verify VendorDocument created in DB
        assertThat(vendorDocumentRepository.findByVendorProfileAndDocType(profile, docType)).isNotEmpty();
    }
}
