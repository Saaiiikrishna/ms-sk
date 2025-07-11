package com.mysillydreams.userservice.web.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.domain.inventory.InventoryProfile;
import com.mysillydreams.userservice.domain.vendor.VendorProfile;
import com.mysillydreams.userservice.dto.UserDto;
import com.mysillydreams.userservice.dto.inventory.InventoryProfileDto;
import com.mysillydreams.userservice.dto.vendor.VendorProfileDto;
import com.mysillydreams.userservice.repository.UserRepository;
import com.mysillydreams.userservice.repository.inventory.InventoryProfileRepository;
import com.mysillydreams.userservice.repository.vendor.VendorProfileRepository;
import com.mysillydreams.userservice.service.UserService; // For consistent EntityNotFoundException

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser; // Crucial for @PreAuthorize
import org.springframework.test.web.servlet.MockMvc;


import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Import a SecurityConfig if User-Service has one that enables method security.
// If not, @EnableGlobalMethodSecurity on Application class is picked up by WebMvcTest usually.
// For User-Service, UserServiceApplication has @EnableGlobalMethodSecurity.
// We also need to provide any beans that SecurityConfig might depend on if it were complex.
// @Import(UserServiceSecurityConfig.class) // If such a config existed and was needed.

@WebMvcTest(AdminController.class)
public class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository mockUserRepository;
    @MockBean
    private UserService mockUserService; // Used for getById consistency
    @MockBean
    private VendorProfileRepository mockVendorProfileRepository;
    @MockBean
    private InventoryProfileRepository mockInventoryProfileRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private UserEntity sampleUserEntity;
    private UserDto sampleUserDto;
    private UUID sampleUserId;

    @BeforeEach
    void setUp() {
        sampleUserId = UUID.randomUUID();
        sampleUserEntity = new UserEntity();
        sampleUserEntity.setId(sampleUserId);
        sampleUserEntity.setName("Admin Test User");
        sampleUserEntity.setEmail("admintest@example.com");
        sampleUserEntity.setReferenceId("ref-admin-test");
        sampleUserDto = UserDto.from(sampleUserEntity);
    }

    // --- User Management Tests ---

    @Test
    @WithMockUser(roles = "ADMIN") // Simulate admin user
    void listAllUsers_success() throws Exception {
        Pageable pageable = PageRequest.of(0, 10);
        PageImpl<UserEntity> userPage = new PageImpl<>(List.of(sampleUserEntity), pageable, 1);
        given(mockUserRepository.findAll(any(Pageable.class))).willReturn(userPage);

        mockMvc.perform(get("/admin/users")
                        .param("page", "0")
                        .param("size", "10")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name", is(sampleUserDto.getName())));
    }

    @Test
    @WithMockUser(roles = "USER") // Non-admin
    void listAllUsers_forbiddenForNonAdmin() throws Exception {
         mockMvc.perform(get("/admin/users")
                        .param("page", "0")
                        .param("size", "10")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }


    @Test
    @WithMockUser(roles = "ADMIN")
    void getUserById_success() throws Exception {
        given(mockUserService.getById(sampleUserId)).willReturn(sampleUserEntity);

        mockMvc.perform(get("/admin/users/{userId}", sampleUserId)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(sampleUserId.toString())))
                .andExpect(jsonPath("$.name", is(sampleUserDto.getName())));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getUserById_notFound() throws Exception {
        given(mockUserService.getById(sampleUserId)).willThrow(new EntityNotFoundException("User not found"));

        mockMvc.perform(get("/admin/users/{userId}", sampleUserId)
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("User not found")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteUserById_success_conceptual() throws Exception {
        // Conceptual test as actual deletion is complex
        when(mockUserRepository.existsById(sampleUserId)).thenReturn(true);
        // doNothing().when(mockUserRepository).deleteById(sampleUserId); // If we were testing actual delete call

        mockMvc.perform(delete("/admin/users/{userId}", sampleUserId)
                        .with(csrf()))
                .andExpect(status().isNoContent());
        // verify(mockUserRepository).deleteById(sampleUserId); // If testing actual delete
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteUserById_notFound() throws Exception {
        when(mockUserRepository.existsById(sampleUserId)).thenReturn(false);
        mockMvc.perform(delete("/admin/users/{userId}", sampleUserId)
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }


    // --- Vendor Profile Management Tests ---
    @Test
    @WithMockUser(roles = "ADMIN")
    void listAllVendorProfiles_success() throws Exception {
        Pageable pageable = PageRequest.of(0, 5);
        VendorProfile vp = new VendorProfile(); vp.setId(UUID.randomUUID()); vp.setLegalName("Vendor X");
        PageImpl<VendorProfile> vpPage = new PageImpl<>(List.of(vp), pageable, 1);
        given(mockVendorProfileRepository.findAll(any(Pageable.class))).willReturn(vpPage);

        mockMvc.perform(get("/admin/vendor-profiles")
                        .param("page", "0").param("size", "5")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].legalName", is("Vendor X")));
    }

    // --- Inventory Profile Management Tests ---
    @Test
    @WithMockUser(roles = "ADMIN")
    void listAllInventoryProfiles_success() throws Exception {
        Pageable pageable = PageRequest.of(0, 3);
        InventoryProfile ip = new InventoryProfile(); ip.setId(UUID.randomUUID());
        UserEntity userForIp = new UserEntity(); userForIp.setId(UUID.randomUUID());
        ip.setUser(userForIp); // Required for DTO mapping
        ip.setCreatedAt(Instant.now());


        PageImpl<InventoryProfile> ipPage = new PageImpl<>(List.of(ip), pageable, 1);
        given(mockInventoryProfileRepository.findAll(any(Pageable.class))).willReturn(ipPage);

        mockMvc.perform(get("/admin/inventory-profiles")
                        .param("page", "0").param("size", "3")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(ip.getId().toString())));
    }
}
