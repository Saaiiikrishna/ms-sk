package com.mysillydreams.userservice.web.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.domain.support.SenderType;
import com.mysillydreams.userservice.domain.support.SupportMessage;
import com.mysillydreams.userservice.domain.support.SupportProfile;
import com.mysillydreams.userservice.domain.support.SupportTicket;
import com.mysillydreams.userservice.domain.support.TicketStatus;
import com.mysillydreams.userservice.dto.support.*;
import com.mysillydreams.userservice.service.support.SupportMessageService;
import com.mysillydreams.userservice.service.support.SupportTicketService;

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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;


import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SupportController.class)
public class SupportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SupportTicketService mockTicketService;
    @MockBean
    private SupportMessageService mockMessageService;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID testCustomerId;
    private UUID testTicketId;
    private UUID testSupportUserId;
    private SupportTicket sampleTicket;
    private SupportTicketDto sampleTicketDto;
    private SupportMessage sampleMessage;
    private SupportMessageDto sampleMessageDto;

    @BeforeEach
    void setUp() {
        testCustomerId = UUID.randomUUID();
        testTicketId = UUID.randomUUID();
        testSupportUserId = UUID.randomUUID();

        sampleTicket = new SupportTicket();
        sampleTicket.setId(testTicketId);
        sampleTicket.setCustomerId(testCustomerId);
        sampleTicket.setSubject("Test Ticket");
        sampleTicket.setStatus(TicketStatus.OPEN);
        sampleTicket.setCreatedAt(Instant.now());
        sampleTicket.setUpdatedAt(Instant.now());
        sampleTicketDto = SupportTicketDto.from(sampleTicket);

        sampleMessage = new SupportMessage();
        sampleMessage.setId(UUID.randomUUID());
        sampleMessage.setTicket(sampleTicket);
        sampleMessage.setMessage("Hello");
        sampleMessage.setSenderId(testCustomerId);
        sampleMessage.setSenderType(SenderType.CUSTOMER);
        sampleMessage.setTimestamp(Instant.now());
        sampleMessageDto = SupportMessageDto.from(sampleMessage);


        Jwt mockJwt = Jwt.withTokenValue("mock-token")
            .header("alg", "none")
            .subject(testCustomerId.toString())
            .claim("preferred_username", "test_customer")
            .build();
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(mockJwt, null,
            List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void setupSupportUserPrincipal() {
        Jwt mockJwtSupport = Jwt.withTokenValue("mock-token-support")
            .header("alg", "none")
            .subject(testSupportUserId.toString())
            .claim("preferred_username", "test_support_agent")
            .build();
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(mockJwtSupport, null,
            List.of(new SimpleGrantedAuthority("ROLE_SUPPORT_USER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void setupAdminPrincipal() {
         Jwt mockJwtAdmin = Jwt.withTokenValue("mock-token-admin")
            .header("alg", "none")
            .subject(UUID.randomUUID().toString())
            .claim("preferred_username", "super_admin")
            .build();
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(mockJwtAdmin, null,
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }


    @Test
    @WithMockUser
    void createTicket_success() throws Exception {
        CreateSupportTicketRequest request = new CreateSupportTicketRequest();
        request.setSubject("New Issue");
        request.setDescription("Details of the new issue.");
        given(mockTicketService.createTicket(eq(testCustomerId), any(CreateSupportTicketRequest.class)))
                .willReturn(sampleTicket);

        mockMvc.perform(post("/support/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subject", is("New Issue")));
    }

    @Test
    @WithMockUser(roles = {"SUPPORT_USER"})
    void listTickets_asSupportUser_success() throws Exception {
        setupSupportUserPrincipal();
        Page<SupportTicketDto> pagedTickets = new PageImpl<>(List.of(sampleTicketDto), PageRequest.of(0,10), 1);
        given(mockTicketService.listActiveTickets(eq(null), any(Pageable.class))).willReturn(pagedTickets);

        mockMvc.perform(get("/support/tickets")
                        .param("page", "0").param("size", "10")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(sampleTicketDto.getId().toString())));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void listTickets_asAdmin_canGetAll_success() throws Exception {
        setupAdminPrincipal();
        Page<SupportTicketDto> pagedTickets = new PageImpl<>(List.of(sampleTicketDto), PageRequest.of(0,10), 1);
        given(mockTicketService.listAllTickets(any(Pageable.class))).willReturn(pagedTickets);

        mockMvc.perform(get("/support/tickets")
                        .param("page", "0").param("size", "10")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }


    @Test
    @WithMockUser
    void getTicketById_success_customerOwnsTicket() throws Exception {
        sampleTicket.setCustomerId(testCustomerId);
        given(mockTicketService.getTicketById(testTicketId)).willReturn(sampleTicket);

        mockMvc.perform(get("/support/tickets/{ticketId}", testTicketId)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(testTicketId.toString())));
    }

    @Test
    @WithMockUser(roles = {"SUPPORT_USER"})
    void getTicketById_success_supportUserAssigned() throws Exception {
        setupSupportUserPrincipal();
        SupportProfile sp = new SupportProfile(); sp.setId(UUID.randomUUID());
        UserEntity supportUserForProfile = new UserEntity(); supportUserForProfile.setId(testSupportUserId);
        sp.setUser(supportUserForProfile);
        sampleTicket.setAssignedTo(sp);
        given(mockTicketService.getTicketById(testTicketId)).willReturn(sampleTicket);

        mockMvc.perform(get("/support/tickets/{ticketId}", testTicketId)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(testTicketId.toString())));
    }


    @Test
    @WithMockUser
    void getTicketById_customerAccessingOthersTicket_forbidden() throws Exception {
        UUID otherCustomerId = UUID.randomUUID();
        sampleTicket.setCustomerId(otherCustomerId);
        given(mockTicketService.getTicketById(testTicketId)).willReturn(sampleTicket);

        mockMvc.perform(get("/support/tickets/{ticketId}", testTicketId)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void postMessage_customerToOwnTicket_success() throws Exception {
        sampleTicket.setCustomerId(testCustomerId);
        given(mockTicketService.getTicketById(testTicketId)).willReturn(sampleTicket);

        CreateSupportMessageRequest msgRequest = new CreateSupportMessageRequest();
        msgRequest.setMessage("Customer reply");

        given(mockMessageService.postMessageToTicket(eq(testTicketId), eq(SenderType.CUSTOMER), eq(testCustomerId), any(CreateSupportMessageRequest.class)))
            .willReturn(sampleMessage);

        mockMvc.perform(post("/support/tickets/{ticketId}/messages", testTicketId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(msgRequest))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message", is("Hello")));
    }

    @Test
    @WithMockUser(roles = {"SUPPORT_USER"})
    void updateTicketStatus_success() throws Exception {
        setupSupportUserPrincipal();
        SupportTicketUpdateDto updateRequest = new SupportTicketUpdateDto();
        updateRequest.setStatus(TicketStatus.IN_PROGRESS);

        sampleTicket.setStatus(TicketStatus.IN_PROGRESS);
        given(mockTicketService.updateTicketStatus(eq(testTicketId), eq(TicketStatus.IN_PROGRESS), eq(null)))
                .willReturn(sampleTicket);

        mockMvc.perform(put("/support/tickets/{ticketId}/status", testTicketId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is(TicketStatus.IN_PROGRESS.toString())));
    }

    @Test
    @WithMockUser(roles = {"SUPPORT_USER"})
    void listTicketsByCustomer_success() throws Exception {
        setupSupportUserPrincipal();
        Page<SupportTicketDto> pagedTickets = new PageImpl<>(List.of(sampleTicketDto), PageRequest.of(0,10), 1);
        given(mockTicketService.listTicketsByCustomerId(eq(testCustomerId), any(Pageable.class))).willReturn(pagedTickets);

        mockMvc.perform(get("/support/tickets/customer/{customerId}", testCustomerId)
                        .param("page", "0").param("size", "10")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }
}
```
