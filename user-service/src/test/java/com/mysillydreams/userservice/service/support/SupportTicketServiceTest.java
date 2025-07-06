package com.mysillydreams.userservice.service.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.userservice.domain.support.SupportProfile;
import com.mysillydreams.userservice.domain.support.SupportTicket;
import com.mysillydreams.userservice.domain.support.TicketStatus;
import com.mysillydreams.userservice.dto.support.CreateSupportTicketRequest;
import com.mysillydreams.userservice.dto.support.SupportTicketDto;
import com.mysillydreams.userservice.repository.support.SupportProfileRepository;
import com.mysillydreams.userservice.repository.support.SupportTicketRepository;
import com.mysillydreams.userservice.service.UserService;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;


import java.time.Instant; // Added
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupportTicketServiceTest {

    @Mock
    private SupportTicketRepository mockTicketRepository;
    @Mock
    private SupportProfileRepository mockSupportProfileRepository;
    @Mock
    private UserService mockUserService;
    @Mock
    private SupportKafkaClient mockSupportKafkaClient;
    @Mock
    private SupportMessageService mockSupportMessageService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @InjectMocks
    private SupportTicketService supportTicketService;

    private UUID testCustomerId;
    private CreateSupportTicketRequest createRequest;
    private SupportTicket sampleTicket;
    private UUID testTicketId;
    // For listActiveTickets tests
    private static final List<TicketStatus> ACTIVE_TICKET_STATUSES_FOR_TEST = Arrays.asList(
            TicketStatus.OPEN, TicketStatus.IN_PROGRESS, TicketStatus.PENDING_CUSTOMER_RESPONSE, TicketStatus.ESCALATED
    );


    @BeforeEach
    void setUp() {
        testCustomerId = UUID.randomUUID();
        testTicketId = UUID.randomUUID();

        createRequest = new CreateSupportTicketRequest();
        createRequest.setSubject("Login Issue");
        createRequest.setDescription("Cannot log in to my account.");

        sampleTicket = new SupportTicket();
        sampleTicket.setId(testTicketId);
        sampleTicket.setCustomerId(testCustomerId);
        sampleTicket.setSubject(createRequest.getSubject());
        sampleTicket.setDescription(createRequest.getDescription());
        sampleTicket.setStatus(TicketStatus.OPEN);
        sampleTicket.setCreatedAt(Instant.now()); // Ensure timestamps are set for DTO mapping
        sampleTicket.setUpdatedAt(Instant.now());
    }

    @Test
    void createTicket_success() {
        when(mockTicketRepository.save(any(SupportTicket.class))).thenReturn(sampleTicket);
        doNothing().when(mockSupportKafkaClient).publishSupportTicketCreated(any(SupportTicket.class));

        SupportTicket createdTicket = supportTicketService.createTicket(testCustomerId, createRequest);

        assertNotNull(createdTicket);
        assertEquals(testCustomerId, createdTicket.getCustomerId());
        assertEquals(createRequest.getSubject(), createdTicket.getSubject());
        assertEquals(TicketStatus.OPEN, createdTicket.getStatus());

        verify(mockTicketRepository).save(any(SupportTicket.class));
        verify(mockSupportKafkaClient).publishSupportTicketCreated(createdTicket);
    }

    @Test
    void getTicketById_exists_returnsTicket() {
        when(mockTicketRepository.findById(testTicketId)).thenReturn(Optional.of(sampleTicket));
        SupportTicket foundTicket = supportTicketService.getTicketById(testTicketId);
        assertEquals(sampleTicket, foundTicket);
    }

    @Test
    void getTicketById_notExists_throwsEntityNotFoundException() {
        when(mockTicketRepository.findById(testTicketId)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> {
            supportTicketService.getTicketById(testTicketId);
        });
    }

    @Test
    void updateTicketStatus_success_assignsAndPublishesEvent() {
        TicketStatus newStatus = TicketStatus.IN_PROGRESS;
        UUID supportProfileId = UUID.randomUUID();
        SupportProfile mockSupportProfile = new SupportProfile();
        mockSupportProfile.setId(supportProfileId);

        when(mockTicketRepository.findById(testTicketId)).thenReturn(Optional.of(sampleTicket));
        when(mockSupportProfileRepository.findById(supportProfileId)).thenReturn(Optional.of(mockSupportProfile));
        when(mockTicketRepository.save(any(SupportTicket.class))).thenReturn(sampleTicket);
        doNothing().when(mockSupportKafkaClient).publishSupportTicketUpdated(any(SupportTicket.class), anyString(), eq(null));

        SupportTicket updatedTicket = supportTicketService.updateTicketStatus(testTicketId, newStatus, supportProfileId);

        assertEquals(newStatus, updatedTicket.getStatus());
        assertEquals(mockSupportProfile, updatedTicket.getAssignedTo());
        verify(mockTicketRepository).save(sampleTicket);
        verify(mockSupportKafkaClient).publishSupportTicketUpdated(updatedTicket, TicketStatus.OPEN.toString(), null);
    }

    @Test
    void updateTicketStatus_unassign_success() {
        sampleTicket.setAssignedTo(new SupportProfile());
        when(mockTicketRepository.findById(testTicketId)).thenReturn(Optional.of(sampleTicket));
        when(mockTicketRepository.save(any(SupportTicket.class))).thenReturn(sampleTicket);

        SupportTicket updatedTicket = supportTicketService.updateTicketStatus(testTicketId, TicketStatus.OPEN, null);

        assertNull(updatedTicket.getAssignedTo());
        verify(mockSupportKafkaClient).publishSupportTicketUpdated(updatedTicket, TicketStatus.OPEN.toString(), null);
    }


    @Test
    void updateTicketStatus_assigneeProfileNotFound_throwsEntityNotFoundException() {
        TicketStatus newStatus = TicketStatus.IN_PROGRESS;
        UUID nonExistentSupportProfileId = UUID.randomUUID();
        when(mockTicketRepository.findById(testTicketId)).thenReturn(Optional.of(sampleTicket));
        when(mockSupportProfileRepository.findById(nonExistentSupportProfileId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> {
            supportTicketService.updateTicketStatus(testTicketId, newStatus, nonExistentSupportProfileId);
        });
    }

    @Test
    void listTicketsByCustomerId_returnsPagedDtos() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<SupportTicket> ticketPage = new PageImpl<>(List.of(sampleTicket), pageable, 1);
        when(mockTicketRepository.findByCustomerId(testCustomerId, pageable)).thenReturn(ticketPage);

        Page<SupportTicketDto> resultPage = supportTicketService.listTicketsByCustomerId(testCustomerId, pageable);

        assertEquals(1, resultPage.getTotalElements());
        assertEquals(sampleTicket.getSubject(), resultPage.getContent().get(0).getSubject());
    }

    @Test
    void listActiveTickets_forSpecificAgent_returnsPagedDtos() {
        Pageable pageable = PageRequest.of(0, 10);
        UUID agentProfileId = UUID.randomUUID();
        Page<SupportTicket> ticketPage = new PageImpl<>(List.of(sampleTicket), pageable, 1);

        when(mockTicketRepository.findByAssignedToIdAndStatusIn(agentProfileId, ACTIVE_TICKET_STATUSES_FOR_TEST, pageable))
            .thenReturn(ticketPage);

        Page<SupportTicketDto> resultPage = supportTicketService.listActiveTickets(agentProfileId, pageable);
        assertEquals(1, resultPage.getTotalElements());
    }

    @Test
    void listActiveTickets_unassigned_returnsPagedDtos() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<SupportTicket> ticketPage = new PageImpl<>(List.of(sampleTicket), pageable, 1);

        when(mockTicketRepository.findByAssignedToIsNullAndStatusIn(ACTIVE_TICKET_STATUSES_FOR_TEST, pageable))
            .thenReturn(ticketPage);

        Page<SupportTicketDto> resultPage = supportTicketService.listActiveTickets(null, pageable);
        assertEquals(1, resultPage.getTotalElements());
    }

    @Test
    void listAllTickets_returnsPagedDtos() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<SupportTicket> ticketPage = new PageImpl<>(List.of(sampleTicket), pageable, 1);
        when(mockTicketRepository.findAll(pageable)).thenReturn(ticketPage);

        Page<SupportTicketDto> resultPage = supportTicketService.listAllTickets(pageable);
        assertEquals(1, resultPage.getTotalElements());
    }
}
