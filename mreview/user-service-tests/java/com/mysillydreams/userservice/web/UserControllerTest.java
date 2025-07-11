package com.mysillydreams.userservice.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.dto.UserDto;
import com.mysillydreams.userservice.service.UserService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;


import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    private UserDto sampleUserDto;
    private UserEntity sampleUserEntity;
    private String sampleRefId;
    private UUID sampleUUID;

    @BeforeEach
    void setUp() {
        sampleUUID = UUID.randomUUID();
        sampleRefId = "ref-" + sampleUUID.toString();

        sampleUserEntity = new UserEntity();
        sampleUserEntity.setId(sampleUUID);
        sampleUserEntity.setReferenceId(sampleRefId);
        sampleUserEntity.setName("Test User");
        sampleUserEntity.setEmail("test@example.com");
        sampleUserEntity.setPhone("1234567890");
        sampleUserEntity.setDob("1990-01-01");
        sampleUserEntity.setGender("OTHER");
        Set<String> roles = new HashSet<>();
        roles.add("ROLE_USER");
        sampleUserEntity.setRoles(roles);
        sampleUserEntity.setCreatedAt(Instant.now());
        sampleUserEntity.setUpdatedAt(Instant.now());

        sampleUserDto = UserDto.from(sampleUserEntity); // Use the DTO's own conversion
    }

    @Test
    void createUser_success() throws Exception {
        given(userService.createUser(any(UserDto.class))).willReturn(sampleUserEntity);

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleUserDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.referenceId", is(sampleRefId)))
                .andExpect(jsonPath("$.name", is(sampleUserDto.getName())))
                .andExpect(jsonPath("$.email", is(sampleUserDto.getEmail())))
                .andExpect(jsonPath("$.roles", hasSize(1)))
                .andExpect(jsonPath("$.roles[0]", is("ROLE_USER")));
    }

    @Test
    void createUser_validationError() throws Exception {
        UserDto invalidDto = new UserDto(); // Missing name, email
        invalidDto.setDob("invalid-date-format"); // also an invalid format

        // We expect Spring's @Valid to catch this before it hits the service if controller args are validated.
        // The GlobalExceptionHandler should then format the error.
        // For this unit test, we are not testing GlobalExceptionHandler directly, but the fact that
        // a 400 is returned due to validation constraints on UserDto.
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest()); // Assuming @Valid is effective
    }

    @Test
    void createUser_emailExists_throwsResponseStatusException() throws Exception {
        given(userService.createUser(any(UserDto.class)))
            .willThrow(new IllegalArgumentException("User with email " + sampleUserDto.getEmail() + " already exists."));

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleUserDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("User with email " + sampleUserDto.getEmail() + " already exists.")));
    }


    @Test
    void getUserByReferenceId_success() throws Exception {
        given(userService.getByReferenceId(sampleRefId)).willReturn(sampleUserEntity);

        mockMvc.perform(get("/users/{referenceId}", sampleRefId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceId", is(sampleRefId)))
                .andExpect(jsonPath("$.name", is(sampleUserDto.getName())));
    }

    @Test
    void getUserByReferenceId_notFound() throws Exception {
        String unknownRefId = "unknown-ref";
        given(userService.getByReferenceId(unknownRefId))
                .willThrow(new EntityNotFoundException("User not found with reference ID: " + unknownRefId));

        mockMvc.perform(get("/users/{referenceId}", unknownRefId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("User not found with reference ID: " + unknownRefId)));
    }

    @Test
    void updateUserProfile_success() throws Exception {
        UserDto updateRequestDto = new UserDto();
        updateRequestDto.setName("Updated Name");
        // Assume sampleUserEntity is the state after update for return
        sampleUserEntity.setName("Updated Name");

        given(userService.updateUser(eq(sampleRefId), any(UserDto.class))).willReturn(sampleUserEntity);

        mockMvc.perform(put("/users/{referenceId}", sampleRefId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Updated Name")));
    }

    @Test
    void updateUserProfile_notFound() throws Exception {
        UserDto updateRequestDto = new UserDto();
        updateRequestDto.setName("Updated Name");
        String unknownRefId = "unknown-ref";

        given(userService.updateUser(eq(unknownRefId), any(UserDto.class)))
            .willThrow(new EntityNotFoundException("User not found with reference ID: " + unknownRefId));

        mockMvc.perform(put("/users/{referenceId}", unknownRefId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequestDto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("User not found with reference ID: " + unknownRefId)));
    }

    @Test
    void updateUserProfile_validationErrorInDto() throws Exception {
         UserDto invalidUpdateDto = new UserDto();
         invalidUpdateDto.setName(""); // Blank name

        mockMvc.perform(put("/users/{referenceId}", sampleRefId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidUpdateDto)))
                .andExpect(status().isBadRequest()); // Assuming @Valid catches this
    }

    @Test
    void updateUserProfile_serviceThrowsIllegalArgument() throws Exception {
        UserDto updateDto = new UserDto(); // Valid DTO structure
        updateDto.setDob("bad-date-format"); // But content that service will reject

        given(userService.updateUser(eq(sampleRefId), any(UserDto.class)))
            .willThrow(new IllegalArgumentException("Invalid Date of Birth format for update. Expected YYYY-MM-DD."));

        mockMvc.perform(put("/users/{referenceId}", sampleRefId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Invalid Date of Birth format for update. Expected YYYY-MM-DD.")));
    }
}
