package com.mysillydreams.userservice.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.userservice.config.UserIntegrationTestBase;
import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.dto.UserDto;
import com.mysillydreams.userservice.repository.UserRepository;
import com.mysillydreams.userservice.service.EncryptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;


import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK) // Use MOCK to avoid port conflicts if other tests run in parallel
@AutoConfigureMockMvc
public class UserControllerIntegrationTest extends UserIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EncryptionService encryptionService; // To verify encryption if needed, or for complex assertions

    @BeforeEach
    @AfterEach
    void cleanupDatabase() {
        userRepository.deleteAll();
    }

    @Test
    void createUser_success_shouldReturnCreatedUserWithEncryptedFieldsStored() throws Exception {
        UserDto requestDto = new UserDto();
        requestDto.setName("Integration Test User");
        requestDto.setEmail("integ-test@example.com");
        requestDto.setPhone("1230987654");
        requestDto.setDob("1985-07-15");
        requestDto.setGender("FEMALE");
        Set<String> rolesRequest = new HashSet<>();
        rolesRequest.add("ROLE_CUSTOMER");
        // Note: UserController/UserService currently don't take roles from DTO for creation.
        // Roles are set by other means (e.g. VendorOnboardingService).
        // For this test, we expect the default (empty) roles or roles set by a default mechanism if any.
        // If UserDto had roles for input, we'd set them here.

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is(requestDto.getName()))) // Response DTO has decrypted name
                .andExpect(jsonPath("$.email", is(requestDto.getEmail()))) // Response DTO has decrypted email
                .andExpect(jsonPath("$.phone", is(requestDto.getPhone())))
                .andExpect(jsonPath("$.dob", is(requestDto.getDob())))
                .andExpect(jsonPath("$.referenceId", notNullValue()))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.roles", hasSize(0))); // Expecting empty roles as DTO doesn't set them

        // Verify data in DB is encrypted
        Optional<UserEntity> foundEntityOpt = userRepository.findByEmail(encryptionService.encrypt(requestDto.getEmail()));
        assertThat(foundEntityOpt).isPresent();
        UserEntity persistedEntity = foundEntityOpt.get();

        // Compare persisted (raw from DB) vs original DTO values
        // Name, email, phone, dob in entity are encrypted. We can't directly compare them to plain DTO values.
        // But we can decrypt them from entity for assertion or check they are not plain.
        assertThat(persistedEntity.getName()).isNotEqualTo(requestDto.getName()); // Should be encrypted
        assertThat(encryptionService.decrypt(persistedEntity.getName())).isEqualTo(requestDto.getName());

        assertThat(persistedEntity.getEmail()).isNotEqualTo(requestDto.getEmail()); // Should be encrypted
        assertThat(encryptionService.decrypt(persistedEntity.getEmail())).isEqualTo(requestDto.getEmail());

        assertThat(persistedEntity.getPhone()).isNotEqualTo(requestDto.getPhone());
        assertThat(encryptionService.decrypt(persistedEntity.getPhone())).isEqualTo(requestDto.getPhone());

        assertThat(persistedEntity.getDob()).isNotEqualTo(requestDto.getDob());
        assertThat(encryptionService.decrypt(persistedEntity.getDob())).isEqualTo(requestDto.getDob());
    }

    @Test
    void createUser_emailExists_shouldReturnBadRequest() throws Exception {
        // First, create a user
        UserEntity existingUser = new UserEntity();
        existingUser.setReferenceId(UUID.randomUUID().toString());
        existingUser.setName("Existing User");
        existingUser.setEmail("existing@example.com"); // This will be encrypted
        userRepository.save(existingUser);

        // Attempt to create another user with the same email
        UserDto requestDto = new UserDto();
        requestDto.setName("New User Same Email");
        requestDto.setEmail("existing@example.com"); // Same email

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isBadRequest()); // Expecting 400 due to existing email
                // The exact error message check depends on GlobalExceptionHandler or controller's direct response
                // .andExpect(jsonPath("$.message", containsString("already exists")));
    }

    @Test
    void getUserByReferenceId_userExists_shouldReturnUserDtoWithDecryptedFields() throws Exception {
        UserEntity user = new UserEntity();
        String refId = "ref-integ-get";
        user.setReferenceId(refId);
        user.setName("Get User Test");
        user.setEmail("getuser.integ@example.com");
        user.setPhone("5551234");
        user.setDob("2001-01-01");
        user.getRoles().add("ROLE_TESTER");
        userRepository.save(user); // Encryption happens via CryptoConverter

        mockMvc.perform(get("/users/{referenceId}", refId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceId", is(refId)))
                .andExpect(jsonPath("$.name", is("Get User Test"))) // Decrypted
                .andExpect(jsonPath("$.email", is("getuser.integ@example.com"))) // Decrypted
                .andExpect(jsonPath("$.phone", is("5551234"))) // Decrypted
                .andExpect(jsonPath("$.dob", is("2001-01-01"))) // Decrypted
                .andExpect(jsonPath("$.roles", hasSize(1)))
                .andExpect(jsonPath("$.roles[0]", is("ROLE_TESTER")));
    }

    @Test
    void getUserByReferenceId_userNotFound_shouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/users/{referenceId}", "non-existent-ref-id"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional // Needed if updateUser modifies collections that are lazily fetched within the same session
    void updateUserProfile_success_shouldUpdateAndReturnDto() throws Exception {
        // 1. Create initial user
        UserEntity initialUser = new UserEntity();
        String refId = "ref-integ-update";
        initialUser.setReferenceId(refId);
        initialUser.setName("Original Name");
        initialUser.setEmail("update.original@example.com");
        initialUser.setPhone("1112223333");
        initialUser.setDob("1990-01-01");
        userRepository.saveAndFlush(initialUser); // Save and flush to DB

        // 2. Prepare update DTO
        UserDto updateDto = new UserDto();
        updateDto.setName("Updated Name by Integration Test");
        updateDto.setPhone("4445556666");
        updateDto.setDob("1992-02-02");
        // Email is not updated by this DTO in current service logic

        // 3. Perform update
        mockMvc.perform(put("/users/{referenceId}", refId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is(updateDto.getName())))
                .andExpect(jsonPath("$.phone", is(updateDto.getPhone())))
                .andExpect(jsonPath("$.dob", is(updateDto.getDob())))
                .andExpect(jsonPath("$.email", is(initialUser.getEmail()))); // Email should be unchanged (decrypted original)

        // 4. Verify in DB
        Optional<UserEntity> updatedEntityOpt = userRepository.findByReferenceId(refId);
        assertThat(updatedEntityOpt).isPresent();
        UserEntity updatedEntity = updatedEntityOpt.get();

        // Check that the fields were indeed updated (and are stored encrypted, so decrypt for check)
        assertThat(encryptionService.decrypt(updatedEntity.getName())).isEqualTo(updateDto.getName());
        assertThat(encryptionService.decrypt(updatedEntity.getPhone())).isEqualTo(updateDto.getPhone());
        assertThat(encryptionService.decrypt(updatedEntity.getDob())).isEqualTo(updateDto.getDob());
        // Email should remain the original encrypted email
        assertThat(encryptionService.decrypt(updatedEntity.getEmail())).isEqualTo(initialUser.getEmail());
    }
}
