package com.mysillydreams.userservice.repository.support;

import com.mysillydreams.userservice.domain.support.SupportMessage;
import com.mysillydreams.userservice.domain.support.SupportTicket;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SupportMessageRepository extends JpaRepository<SupportMessage, UUID> {

    /**
     * Finds all messages for a given support ticket, ordered by timestamp.
     *
     * @param ticket The SupportTicket entity.
     * @param sort   Sorting criteria (typically by timestamp).
     * @return A list of {@link SupportMessage}.
     */
    List<SupportMessage> findByTicket(SupportTicket ticket, Sort sort);

    /**
     * Finds all messages for a given support ticket ID, ordered by timestamp.
     *
     * @param ticketId The UUID of the SupportTicket.
     * @param sort     Sorting criteria.
     * @return A list of {@link SupportMessage}.
     */
    List<SupportMessage> findByTicketId(UUID ticketId, Sort sort);

    // Additional query methods could be added if needed, e.g.,
    // - Find messages by senderId
    // - Find messages containing certain keywords (would require full-text search capabilities
    //   or simple LIKE, but LIKE is inefficient and problematic with encrypted message content)
}
