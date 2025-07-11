package com.mysillydreams.delivery.repository;

import com.mysillydreams.delivery.domain.DeliveryEvent;
import com.mysillydreams.delivery.domain.enums.DeliveryEventType; // Corrected import
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeliveryEventRepository extends JpaRepository<DeliveryEvent, UUID> {
    List<DeliveryEvent> findByAssignmentIdOrderByOccurredAtDesc(UUID assignmentId);
    List<DeliveryEvent> findByAssignmentIdAndEventTypeOrderByOccurredAtDesc(UUID assignmentId, DeliveryEventType eventType);
}
