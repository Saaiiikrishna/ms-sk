package com.mysillydreams.userservice.repository.support;

import com.mysillydreams.userservice.config.UserIntegrationTestBase;
import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.domain.support.SupportProfile;
import com.mysillydreams.userservice.domain.support.SupportTicket;
import com.mysillydreams.userservice.domain.support.TicketStatus;
import com.mysillydreams.userservice.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ContextConfiguration;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(initializers = UserIntegrationTestBase.Initializer.class)
public class SupportTicketRepositoryTest {

    @Autowired
    private SupportTicketRepository supportTicketRepository;
    @Autowired
    private SupportProfileRepository supportProfileRepository;
    @Autowired
    private UserRepository userRepository;

    private UserEntity customerUser1, customerUser2;
    private SupportProfile supportAgent1, supportAgent2;

    @BeforeEach
    void setUp() {
        supportTicketRepository.deleteAll();
        supportProfileRepository.deleteAll();
        userRepository.deleteAll();

        customerUser1 = userRepository.save(new UserEntity() {{ setReferenceId("cust1"); setEmail("cust1@example.com"); }});
        customerUser2 = userRepository.save(new UserEntity() {{ setReferenceId("cust2"); setEmail("cust2@example.com"); }});

        UserEntity agentUser1 = userRepository.save(new UserEntity() {{ setReferenceId("agent1"); setEmail("agent1@example.com"); }});
        UserEntity agentUser2 = userRepository.save(new UserEntity() {{ setReferenceId("agent2"); setEmail("agent2@example.com"); }});

        supportAgent1 = supportProfileRepository.save(new SupportProfile() {{ setUser(agentUser1); setSpecialization("Tech"); }});
        supportAgent2 = supportProfileRepository.save(new SupportProfile() {{ setUser(agentUser2); setSpecialization("Billing"); }});
        userRepository.flush();
        supportProfileRepository.flush();
    }

    @AfterEach
    void tearDown() {
        supportTicketRepository.deleteAll();
        supportProfileRepository.deleteAll();
        userRepository.deleteAll();
    }


    private SupportTicket createAndSaveTicket(UUID customerId, String subject, TicketStatus status, SupportProfile assignedTo) {
        SupportTicket ticket = new SupportTicket();
        ticket.setCustomerId(customerId);
        ticket.setSubject(subject);
        ticket.setDescription("Description for " + subject);
        ticket.setStatus(status);
        ticket.setAssignedTo(assignedTo);
        return supportTicketRepository.saveAndFlush(ticket);
    }

    @Test
    void findByCustomerId_returnsPagedTickets() {
        createAndSaveTicket(customerUser1.getId(), "Ticket A1", TicketStatus.OPEN, supportAgent1);
        createAndSaveTicket(customerUser1.getId(), "Ticket A2", TicketStatus.IN_PROGRESS, null);
        createAndSaveTicket(customerUser2.getId(), "Ticket B1", TicketStatus.OPEN, supportAgent1);

        Pageable pageable = PageRequest.of(0, 5, Sort.by("subject"));
        Page<SupportTicket> customer1Tickets = supportTicketRepository.findByCustomerId(customerUser1.getId(), pageable);

        assertThat(customer1Tickets.getTotalElements()).isEqualTo(2);
        assertThat(customer1Tickets.getContent()).extracting(SupportTicket::getSubject).containsExactly("Ticket A1", "Ticket A2");
    }

    @Test
    void findByStatus_returnsPagedTickets() {
        createAndSaveTicket(customerUser1.getId(), "T1", TicketStatus.OPEN, null);
        createAndSaveTicket(customerUser2.getId(), "T2", TicketStatus.RESOLVED, supportAgent1);
        createAndSaveTicket(customerUser1.getId(), "T3", TicketStatus.OPEN, supportAgent2);

        Pageable pageable = PageRequest.of(0, 5);
        Page<SupportTicket> openTickets = supportTicketRepository.findByStatus(TicketStatus.OPEN, pageable);
        assertThat(openTickets.getTotalElements()).isEqualTo(2);
        assertThat(openTickets.getContent()).extracting(SupportTicket::getSubject).contains("T1", "T3");
    }

    @Test
    void findByStatusIn_returnsPagedTickets() {
        createAndSaveTicket(customerUser1.getId(), "T-Open", TicketStatus.OPEN, null);
        createAndSaveTicket(customerUser2.getId(), "T-InProgress", TicketStatus.IN_PROGRESS, supportAgent1);
        createAndSaveTicket(customerUser1.getId(), "T-Resolved", TicketStatus.RESOLVED, supportAgent2);

        List<TicketStatus> statuses = Arrays.asList(TicketStatus.OPEN, TicketStatus.IN_PROGRESS);
        Pageable pageable = PageRequest.of(0, 5);
        Page<SupportTicket> activeTickets = supportTicketRepository.findByStatusIn(statuses, pageable);

        assertThat(activeTickets.getTotalElements()).isEqualTo(2);
        assertThat(activeTickets.getContent()).extracting(SupportTicket::getSubject).contains("T-Open", "T-InProgress");
    }

    @Test
    void findByAssignedTo_returnsPagedTickets() {
        createAndSaveTicket(customerUser1.getId(), "Agent1-T1", TicketStatus.OPEN, supportAgent1);
        createAndSaveTicket(customerUser2.getId(), "Agent1-T2", TicketStatus.IN_PROGRESS, supportAgent1);
        createAndSaveTicket(customerUser1.getId(), "Agent2-T1", TicketStatus.OPEN, supportAgent2);

        Pageable pageable = PageRequest.of(0, 5, Sort.by("subject"));
        Page<SupportTicket> agent1Tickets = supportTicketRepository.findByAssignedTo(supportAgent1, pageable);
        assertThat(agent1Tickets.getTotalElements()).isEqualTo(2);
        assertThat(agent1Tickets.getContent()).extracting(SupportTicket::getSubject).containsExactly("Agent1-T1", "Agent1-T2");
    }

    @Test
    void findByAssignedToId_returnsPagedTickets() {
        createAndSaveTicket(customerUser1.getId(), "Agent1-T1 by ID", TicketStatus.OPEN, supportAgent1);
        Pageable pageable = PageRequest.of(0, 5);
        Page<SupportTicket> agent1Tickets = supportTicketRepository.findByAssignedToId(supportAgent1.getId(), pageable);
        assertThat(agent1Tickets.getTotalElements()).isEqualTo(1);
    }


    @Test
    void findActiveTicketsForAgentOrUnassigned_forSpecificAgent() {
        createAndSaveTicket(customerUser1.getId(), "Open For Agent1", TicketStatus.OPEN, supportAgent1);
        createAndSaveTicket(customerUser2.getId(), "Progress For Agent1", TicketStatus.IN_PROGRESS, supportAgent1);
        createAndSaveTicket(customerUser1.getId(), "Resolved For Agent1", TicketStatus.RESOLVED, supportAgent1); // Not active
        createAndSaveTicket(customerUser2.getId(), "Open Unassigned", TicketStatus.OPEN, null); // Unassigned
        createAndSaveTicket(customerUser1.getId(), "Open For Agent2", TicketStatus.OPEN, supportAgent2); // Different Agent

        List<TicketStatus> activeStatuses = Arrays.asList(TicketStatus.OPEN, TicketStatus.IN_PROGRESS, TicketStatus.PENDING_CUSTOMER_RESPONSE);
        Pageable pageable = PageRequest.of(0, 10, Sort.by("subject"));

        Page<SupportTicket> agent1ActiveTickets = supportTicketRepository.findActiveTicketsForAgentOrUnassigned(
            activeStatuses, supportAgent1.getId(), pageable
        );
        assertThat(agent1ActiveTickets.getTotalElements()).isEqualTo(2);
        assertThat(agent1ActiveTickets.getContent()).extracting(SupportTicket::getSubject)
            .containsExactly("Open For Agent1", "Progress For Agent1");
    }

    @Test
    void findActiveTicketsForAgentOrUnassigned_forUnassigned() {
        createAndSaveTicket(customerUser1.getId(), "Open For Agent1", TicketStatus.OPEN, supportAgent1);
        createAndSaveTicket(customerUser2.getId(), "Open Unassigned", TicketStatus.OPEN, null);
        createAndSaveTicket(customerUser1.getId(), "Progress Unassigned", TicketStatus.IN_PROGRESS, null);
        createAndSaveTicket(customerUser2.getId(), "Resolved Unassigned", TicketStatus.RESOLVED, null); // Not active

        List<TicketStatus> activeStatuses = Arrays.asList(TicketStatus.OPEN, TicketStatus.IN_PROGRESS, TicketStatus.PENDING_CUSTOMER_RESPONSE);
        Pageable pageable = PageRequest.of(0, 10, Sort.by("subject"));

        Page<SupportTicket> unassignedActiveTickets = supportTicketRepository.findActiveTicketsForAgentOrUnassigned(
            activeStatuses, null, pageable // null for agentId means unassigned
        );
        assertThat(unassignedActiveTickets.getTotalElements()).isEqualTo(2);
        assertThat(unassignedActiveTickets.getContent()).extracting(SupportTicket::getSubject)
            .containsExactly("Open Unassigned", "Progress Unassigned");
    }


    @Test
    void findBySubjectContainingIgnoreCase_returnsMatchingTickets() {
        createAndSaveTicket(customerUser1.getId(), "Urgent: Login Issue", TicketStatus.OPEN, null);
        createAndSaveTicket(customerUser2.getId(), "Payment problem", TicketStatus.IN_PROGRESS, supportAgent1);
        createAndSaveTicket(customerUser1.getId(), "Cannot reset password (urgent)", TicketStatus.OPEN, null);

        Pageable pageable = PageRequest.of(0, 5);
        Page<SupportTicket> urgentTickets = supportTicketRepository.findBySubjectContainingIgnoreCase("urgent", pageable);
        assertThat(urgentTickets.getTotalElements()).isEqualTo(2);
        assertThat(urgentTickets.getContent()).extracting(SupportTicket::getSubject).contains("Urgent: Login Issue", "Cannot reset password (urgent)");
    }
}
