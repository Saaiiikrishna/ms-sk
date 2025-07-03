package com.mysillydreams.userservice.repository.support;

import com.mysillydreams.userservice.domain.support.SupportProfile;
import com.mysillydreams.userservice.domain.support.SupportTicket;
import com.mysillydreams.userservice.domain.support.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {

    /**
     * Finds all tickets for a given customer ID, ordered by creation date.
     *
     * @param customerId The UUID of the customer (UserEntity ID).
     * @param pageable   Pagination information.
     * @return A page of {@link SupportTicket}.
     */
    Page<SupportTicket> findByCustomerId(UUID customerId, Pageable pageable);

    /**
     * Finds all tickets with a specific status, or a collection of statuses.
     *
     * @param status   The status to filter by.
     * @param pageable Pagination information.
     * @return A page of {@link SupportTicket}.
     */
    Page<SupportTicket> findByStatus(TicketStatus status, Pageable pageable);
    Page<SupportTicket> findByStatusIn(Collection<TicketStatus> statuses, Pageable pageable);


    /**
     * Finds all tickets assigned to a specific support profile (agent).
     *
     * @param assignedTo The SupportProfile of the agent.
     * @param pageable   Pagination information.
     * @return A page of {@link SupportTicket}.
     */
    Page<SupportTicket> findByAssignedTo(SupportProfile assignedTo, Pageable pageable);
    Page<SupportTicket> findByAssignedToId(UUID assignedToSupportProfileId, Pageable pageable);


    /**
     * Finds tickets that are not in a terminal status (e.g., RESOLVED, CLOSED)
     * and are either unassigned or assigned to a specific support agent.
     * Useful for a support agent's work queue.
     *
     * @param nonTerminalStatuses A collection of statuses considered non-terminal (e.g., OPEN, IN_PROGRESS, PENDING_CUSTOMER_RESPONSE).
     * @param assignedToSupportProfileId The ID of the support agent. Can be null to find unassigned tickets.
     * @param pageable Pagination information.
     * @return A page of {@link SupportTicket}.
     */
    @Query("SELECT st FROM SupportTicket st WHERE st.status IN :nonTerminalStatuses AND (st.assignedTo.id = :assignedToSupportProfileId OR (:assignedToSupportProfileId IS NULL AND st.assignedTo IS NULL))")
    Page<SupportTicket> findActiveTicketsForAgentOrUnassigned(
            @Param("nonTerminalStatuses") Collection<TicketStatus> nonTerminalStatuses,
            @Param("assignedToSupportProfileId") UUID assignedToSupportProfileId, // Null for unassigned
            Pageable pageable
    );

    // Simpler version if we always want assigned to a specific agent or all unassigned (not combined)
    Page<SupportTicket> findByAssignedToIdAndStatusIn(UUID assignedToSupportProfileId, Collection<TicketStatus> statuses, Pageable pageable);
    Page<SupportTicket> findByAssignedToIsNullAndStatusIn(Collection<TicketStatus> statuses, Pageable pageable);


    /**
     * Finds tickets by subject containing a keyword (case-insensitive).
     * Note: If subject is encrypted, this type of search won't work directly on the DB column.
     *
     * @param keyword  The keyword to search for in the subject.
     * @param pageable Pagination information.
     * @return A page of {@link SupportTicket}.
     */
    Page<SupportTicket> findBySubjectContainingIgnoreCase(String keyword, Pageable pageable);
}
