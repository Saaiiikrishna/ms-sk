package com.mysillydreams.userservice.web.vendor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.domain.vendor.VendorProfile;
import com.mysillydreams.userservice.domain.vendor.VendorStatus;
import com.mysillydreams.userservice.dto.vendor.PresignedUrlResponse;
import com.mysillydreams.userservice.dto.vendor.RegisterVendorRequest;
import com.mysillydreams.userservice.dto.vendor.VendorProfileDto;
import com.mysillydreams.userservice.service.UserService;
import com.mysillydreams.userservice.service.vendor.DocumentService;
import com.mysillydreams.userservice.service.vendor.VendorOnboardingService;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;


import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(VendorOnboardingController.class)
public class VendorOnboardingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VendorOnboardingService mockVendorOnboardingService;
    @MockBean
    private DocumentService mockDocumentService;
    @MockBean
    private UserService mockUserService; // To mock fetching UserEntity

    @Autowired
    private ObjectMapper objectMapper;

    private UUID testUserId;
    private UserEntity testUserEntity;
    private VendorProfile testVendorProfile;
    private VendorProfileDto testVendorProfileDto;
    private RegisterVendorRequest testRegisterRequest;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();

        testUserEntity = new UserEntity();
        testUserEntity.setId(testUserId);
        testUserEntity.setReferenceId("user-ref-123");
        testUserEntity.setEmail("vendor@example.com");

        testRegisterRequest = new RegisterVendorRequest();
        testRegisterRequest.setLegalName("Super Vendor Inc.");

        testVendorProfile = new VendorProfile();
        testVendorProfile.setId(UUID.randomUUID());
        testVendorProfile.setUser(testUserEntity);
        testVendorProfile.setLegalName(testRegisterRequest.getLegalName());
        testVendorProfile.setStatus(VendorStatus.KYC_IN_PROGRESS);
        testVendorProfile.setKycWorkflowId("wf-test-123");

        testVendorProfileDto = VendorProfileDto.from(testVendorProfile);
    }

    @Test
    void registerVendor_success() throws Exception {
        given(mockUserService.getById(testUserId)).willReturn(testUserEntity);
        given(mockVendorOnboardingService.registerVendor(any(RegisterVendorRequest.class), eq(testUserEntity)))
                .willReturn(testVendorProfile);

        mockMvc.perform(post("/vendor-onboarding/register")
                        .header("X-User-Id", testUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testRegisterRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(testVendorProfile.getId().toString())))
                .andExpect(jsonPath("$.legalName", is(testRegisterRequest.getLegalName())))
                .andExpect(jsonPath("$.status", is(VendorStatus.KYC_IN_PROGRESS.toString())));
    }

    @Test
    void registerVendor_userNotFound() throws Exception {
        given(mockUserService.getById(testUserId))
                .willThrow(new EntityNotFoundException("User not found for ID: " + testUserId));

        mockMvc.perform(post("/vendor-onboarding/register")
                        .header("X-User-Id", testUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testRegisterRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("User not found: User not found for ID: " + testUserId)));
    }

    @Test
    void registerVendor_userAlreadyVendor_returnsBadRequest() throws Exception {
        given(mockUserService.getById(testUserId)).willReturn(testUserEntity);
        given(mockVendorOnboardingService.registerVendor(any(RegisterVendorRequest.class), eq(testUserEntity)))
            .willThrow(new IllegalStateException("User already has an active vendor profile."));

        mockMvc.perform(post("/vendor-onboarding/register")
                        .header("X-User-Id", testUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testRegisterRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("User already has an active vendor profile.")));
    }


    @Test
    void getVendorProfile_success() throws Exception {
        given(mockVendorOnboardingService.getProfileByUserId(testUserId)).willReturn(testVendorProfileDto);

        mockMvc.perform(get("/vendor-onboarding/profile")
                        .header("X-User-Id", testUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(testVendorProfileDto.getId().toString())))
                .andExpect(jsonPath("$.legalName", is(testVendorProfileDto.getLegalName())));
    }

    @Test
    void getVendorProfile_notFound() throws Exception {
        given(mockVendorOnboardingService.getProfileByUserId(testUserId))
                .willThrow(new EntityNotFoundException("No vendor profile found for user ID: " + testUserId));

        mockMvc.perform(get("/vendor-onboarding/profile")
                        .header("X-User-Id", testUserId.toString()))
                .andExpect(status().isNotFound())
                 .andExpect(jsonPath("$.message", is("No vendor profile found for user ID: " + testUserId)));
    }

    @Test
    void generateDocumentUploadUrl_success() throws Exception {
        String docType = "PAN_CARD";
        PresignedUrlResponse presignedResponse = new PresignedUrlResponse("https://s3.url/signed", "s3key123");

        // Mock the chain: get user -> get vendor profile entity -> generate URL
        given(mockVendorOnboardingService.getVendorProfileEntityByUserId(testUserId)).willReturn(testVendorProfile);
        given(mockDocumentService.generateUploadUrl(testVendorProfile.getId(), docType)).willReturn(presignedResponse);

        mockMvc.perform(post("/vendor-onboarding/documents/upload-url")
                        .header("X-User-Id", testUserId.toString())
                        .param("docType", docType))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", is("https://s3.url/signed")))
                .andExpect(jsonPath("$.key", is("s3key123")));
    }

    @Test
    void generateDocumentUploadUrl_vendorProfileNotFound() throws Exception {
        String docType = "GSTIN";
        given(mockVendorOnboardingService.getVendorProfileEntityByUserId(testUserId))
            .willThrow(new EntityNotFoundException("Vendor profile not found for user ID: " + testUserId));

        mockMvc.perform(post("/vendor-onboarding/documents/upload-url")
                        .header("X-User-Id", testUserId.toString())
                        .param("docType", docType))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("Vendor profile not found for user ID: " + testUserId)));
    }

    @Test
    void generateDocumentUploadUrl_missingDocType() throws Exception {
        // For @RequestParam @NotBlank, this should be caught by Spring's validation mechanism
        // before it even hits the controller method logic if correctly configured.
        // WebMvcTest might not fully trigger this bean validation on params always,
        // but the controller itself or service layer should ideally validate.
        // The test here checks if the controller handles it (e.g. if it relies on @Validated at class level for params)
        // or if the framework itself returns 400.
        mockMvc.perform(post("/vendor-onboarding/documents/upload-url")
                        .header("X-User-Id", testUserId.toString())) // Missing docType param
                .andExpect(status().isBadRequest()); // Default Spring behavior for missing required param
    }
}
