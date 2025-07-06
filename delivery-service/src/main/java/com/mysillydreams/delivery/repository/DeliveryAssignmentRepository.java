package com.mysillydreams.delivery.repository;

import com.mysillydreams.delivery.domain.DeliveryAssignment;
import com.mysillydreams.delivery.domain.enums.DeliveryAssignmentStatus; // Corrected import if enum is used
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeliveryAssignmentRepository extends JpaRepository<DeliveryAssignment, UUID> {
    Optional<DeliveryAssignment> findByOrderId(UUID orderId);
    List<DeliveryAssignment> findByCourierIdAndStatus(UUID courierId, DeliveryAssignmentStatus status);
    List<DeliveryAssignment> findByStatus(DeliveryAssignmentStatus status);
}
