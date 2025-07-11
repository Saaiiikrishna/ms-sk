package com.mysillydreams.userservice.web.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.userservice.config.UserIntegrationTestBase; // Base with PG, Vault, LocalStack
import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.domain.inventory.InventoryProfile;
import com.mysillydreams.userservice.dto.inventory.InventoryProfileDto;
import com.mysillydreams.userservice.repository.UserRepository;
import com.mysillydreams.userservice.repository.inventory.InventoryProfileRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka; // For Kafka setup specific to this test class if needed, or rely on global if available
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;


import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
// EmbeddedKafka can be added here if InventoryOnboardingService directly produces to Kafka
// For now, it doesn't, so not strictly needed here but harmless if UserIntegrationTestBase doesn't set it up.
// @EmbeddedKafka(...)
public class InventoryOnboardingControllerIntegrationTest extends UserIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private InventoryProfileRepository inventoryProfileRepository;

    private UserEntity testUser;
    private UUID testUserId;

    @BeforeEach
    void setUpUser() {
        inventoryProfileRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        testUser = new UserEntity();
        testUser.setReferenceId("inv-onboard-ctrl-user-" + UUID.randomUUID());
        testUser.setEmail(testUser.getReferenceId() + "@example.com");
        testUser.setName("Inventory Onboarding User");
        testUser.setRoles(new HashSet<>(Set.of("ROLE_USER"))); // Start with a basic role
        testUser = userRepository.saveAndFlush(testUser);
        testUserId = testUser.getId();
    }

    @AfterEach
    void tearDownData() {
        inventoryProfileRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }


    @Test
    void registerInventoryUser_newUserToInventory_createsProfileAndAssignsRole() throws Exception {
        mockMvc.perform(post("/inventory-onboarding/register")
                        .header("X-User-Id", testUserId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId", is(testUserId.toString())))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.createdAt", notNullValue()));

        // Verify DB state
        Optional<UserEntity> updatedUserOpt = userRepository.findById(testUserId);
        assertThat(updatedUserOpt).isPresent();
        assertThat(updatedUserOpt.get().getRoles()).contains("ROLE_INVENTORY_USER", "ROLE_USER");

        Optional<InventoryProfile> profileOpt = inventoryProfileRepository.findByUserId(testUserId);
        assertThat(profileOpt).isPresent();
        assertThat(profileOpt.get().getUser().getId()).isEqualTo(testUserId);
    }

    @Test
    void registerInventoryUser_alreadyInventoryUser_returnsExistingProfile() throws Exception {
        // First, register the user as an inventory user
        InventoryProfile existingProfile = new InventoryProfile();
        existingProfile.setUser(testUser);
        testUser.getRoles().add("ROLE_INVENTORY_USER"); // Manually add role as service would
        userRepository.saveAndFlush(testUser); // Save user with role
        existingProfile = inventoryProfileRepository.saveAndFlush(existingProfile);


        mockMvc.perform(post("/inventory-onboarding/register")
                        .header("X-User-Id", testUserId.toString()))
                .andExpect(status().isOk()) // Controller logic returns OK if profile exists
                .andExpect(jsonPath("$.id", is(existingProfile.getId().toString())))
                .andExpect(jsonPath("$.userId", is(testUserId.toString())));
    }

    @Test
    void registerInventoryUser_userNotFound_returnsNotFound() throws Exception {
        UUID nonExistentUserId = UUID.randomUUID();
        mockMvc.perform(post("/inventory-onboarding/register")
                        .header("X-User-Id", nonExistentUserId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getInventoryProfile_whenExists_returnsProfile() throws Exception {
        InventoryProfile profile = new InventoryProfile();
        profile.setUser(testUser);
        profile = inventoryProfileRepository.saveAndFlush(profile);

        mockMvc.perform(get("/inventory-onboarding/profile")
                        .header("X-User-Id", testUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(profile.getId().toString())))
                .andExpect(jsonPath("$.userId", is(testUserId.toString())));
    }

    @Test
    void getInventoryProfile_whenNotExists_returnsNotFound() throws Exception {
        mockMvc.perform(get("/inventory-onboarding/profile")
                        .header("X-User-Id", testUserId.toString())) // This user has no inventory profile yet
                .andExpect(status().isNotFound());
    }

    @Test
    void registerInventoryUser_missingHeader_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/inventory-onboarding/register"))
                .andExpect(status().isBadRequest());
    }
}
