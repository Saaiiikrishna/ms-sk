package com.mysillydreams.userservice.repository.delivery;

import com.mysillydreams.userservice.domain.delivery.DeliveryEvent;
import com.mysillydreams.userservice.domain.delivery.OrderAssignment;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeliveryEventRepository extends JpaRepository<DeliveryEvent, UUID> {

    /**
     * Finds all delivery events for a given order assignment, ordered by timestamp.
     *
     * @param assignment The order assignment.
     * @param sort The sorting criteria (typically by timestamp).
     * @return A list of {@link DeliveryEvent}.
     */
    List<DeliveryEvent> findByAssignment(OrderAssignment assignment, Sort sort);

    /**
     * Finds all delivery events for a given order assignment ID, ordered by timestamp.
     *
     * @param assignmentId The UUID of the order assignment.
     * @param sort The sorting criteria.
     * @return A list of {@link DeliveryEvent}.
     */
    List<DeliveryEvent> findByAssignmentId(UUID assignmentId, Sort sort);

    /**
     * Finds delivery events for a given assignment and of a specific event type.
     * Useful for checking if a particular event (e.g., "PHOTO_TAKEN") has occurred.
     *
     * @param assignment The order assignment.
     * @param eventType The type of the event string.
     * @param sort Sorting criteria (e.g., if multiple events of same type can occur).
     * @return A list of matching {@link DeliveryEvent}.
     */
    List<DeliveryEvent> findByAssignmentAndEventType(OrderAssignment assignment, String eventType, Sort sort);

    /**
     * Counts delivery events for a given assignment and of a specific event type.
     *
     * @param assignment The order assignment.
     * @param eventType The type of the event string.
     * @return The count of matching events.
     */
    long countByAssignmentAndEventType(OrderAssignment assignment, String eventType);
}
