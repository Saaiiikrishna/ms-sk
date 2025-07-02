package com.mysillydreams.userservice.web.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.domain.inventory.InventoryProfile;
import com.mysillydreams.userservice.dto.inventory.InventoryProfileDto;
import com.mysillydreams.userservice.service.inventory.InventoryOnboardingService;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InventoryOnboardingController.class)
public class InventoryOnboardingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventoryOnboardingService mockInventoryOnboardingService;

    @Autowired
    private ObjectMapper objectMapper; // For request body if needed, not for these endpoints

    private UUID testUserId;
    private InventoryProfileDto testInventoryProfileDto;
    private InventoryProfile testInventoryProfile;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();

        testInventoryProfile = new InventoryProfile();
        testInventoryProfile.setId(UUID.randomUUID());
        UserEntity user = new UserEntity(); // Simplified for DTO creation
        user.setId(testUserId);
        testInventoryProfile.setUser(user);
        testInventoryProfile.setCreatedAt(Instant.now());

        testInventoryProfileDto = InventoryProfileDto.from(testInventoryProfile);
    }

    @Test
    void registerInventoryUser_whenNotAlreadyRegistered_createsProfileAndReturnsCreated() throws Exception {
        // Simulate service throwing EntityNotFound first (meaning no existing profile)
        when(mockInventoryOnboardingService.getInventoryProfileByUserId(testUserId))
                .thenThrow(new EntityNotFoundException("Profile not found"));
        // Then simulate successful registration
        when(mockInventoryOnboardingService.registerInventoryUser(testUserId)).thenReturn(testInventoryProfile);

        mockMvc.perform(post("/inventory-onboarding/register")
                        .header("X-User-Id", testUserId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(testInventoryProfile.getId().toString())))
                .andExpect(jsonPath("$.userId", is(testUserId.toString())));
    }

    @Test
    void registerInventoryUser_whenAlreadyRegistered_returnsExistingProfileAndOk() throws Exception {
        // Simulate service returning existing profile DTO on the first call
        when(mockInventoryOnboardingService.getInventoryProfileByUserId(testUserId)).thenReturn(testInventoryProfileDto);

        mockMvc.perform(post("/inventory-onboarding/register")
                        .header("X-User-Id", testUserId.toString()))
                .andExpect(status().isOk()) // As per controller scaffold logic
                .andExpect(jsonPath("$.id", is(testInventoryProfileDto.getId().toString())))
                .andExpect(jsonPath("$.userId", is(testInventoryProfileDto.getUserId().toString())));
    }

    @Test
    void registerInventoryUser_userServiceThrowsNotFound_returnsNotFound() throws Exception {
        when(mockInventoryOnboardingService.getInventoryProfileByUserId(testUserId))
            .thenThrow(new EntityNotFoundException("Profile not found")); // First call for check
        when(mockInventoryOnboardingService.registerInventoryUser(testUserId))
            .thenThrow(new EntityNotFoundException("User not found")); // Actual registration fails due to user not found

        mockMvc.perform(post("/inventory-onboarding/register")
                        .header("X-User-Id", testUserId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("User not found")));
    }

    @Test
    void registerInventoryUser_serviceThrowsIllegalState_returnsBadRequest() throws Exception {
         when(mockInventoryOnboardingService.getInventoryProfileByUserId(testUserId))
            .thenThrow(new EntityNotFoundException("Profile not found"));
        when(mockInventoryOnboardingService.registerInventoryUser(testUserId))
            .thenThrow(new IllegalStateException("Some registration business rule failed"));

        mockMvc.perform(post("/inventory-onboarding/register")
                        .header("X-User-Id", testUserId.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Some registration business rule failed")));
    }


    @Test
    void getInventoryProfile_whenExists_returnsProfileDto() throws Exception {
        when(mockInventoryOnboardingService.getInventoryProfileByUserId(testUserId)).thenReturn(testInventoryProfileDto);

        mockMvc.perform(get("/inventory-onboarding/profile")
                        .header("X-User-Id", testUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(testInventoryProfileDto.getId().toString())))
                .andExpect(jsonPath("$.userId", is(testInventoryProfileDto.getUserId().toString())));
    }

    @Test
    void getInventoryProfile_whenNotExists_returnsNotFound() throws Exception {
        when(mockInventoryOnboardingService.getInventoryProfileByUserId(testUserId))
                .thenThrow(new EntityNotFoundException("Not an inventory user"));

        mockMvc.perform(get("/inventory-onboarding/profile")
                        .header("X-User-Id", testUserId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("Not an inventory user")));
    }

    @Test
    void registerInventoryUser_missingHeader_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/inventory-onboarding/register"))
                .andExpect(status().isBadRequest()); // Spring MVC handles missing required header
    }

    @Test
    void getInventoryProfile_missingHeader_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/inventory-onboarding/profile"))
                .andExpect(status().isBadRequest());
    }
}
