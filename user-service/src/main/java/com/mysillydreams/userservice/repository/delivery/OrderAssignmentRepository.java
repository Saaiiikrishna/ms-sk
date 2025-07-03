package com.mysillydreams.userservice.repository.delivery;

import com.mysillydreams.userservice.domain.delivery.AssignmentStatus;
import com.mysillydreams.userservice.domain.delivery.DeliveryProfile;
import com.mysillydreams.userservice.domain.delivery.OrderAssignment;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderAssignmentRepository extends JpaRepository<OrderAssignment, UUID> {

    /**
     * Finds all order assignments for a given delivery profile, ordered by assignedAt timestamp.
     *
     * @param deliveryProfile The delivery profile.
     * @param sort The sorting criteria (e.g., by assignedAt).
     * @return A list of {@link OrderAssignment}.
     */
    List<OrderAssignment> findByDeliveryProfile(DeliveryProfile deliveryProfile, Sort sort);

    /**
     * Finds all order assignments for a given delivery profile ID, ordered by assignedAt timestamp.
     *
     * @param deliveryProfileId The UUID of the delivery profile.
     * @param sort The sorting criteria.
     * @return A list of {@link OrderAssignment}.
     */
    List<OrderAssignment> findByDeliveryProfileId(UUID deliveryProfileId, Sort sort);

    /**
     * Finds order assignments for a given delivery profile that are in one of the specified statuses.
     *
     * @param deliveryProfile The delivery profile.
     * @param statuses A collection of {@link AssignmentStatus} to filter by.
     * @param sort Sorting criteria.
     * @return A list of matching {@link OrderAssignment}.
     */
    List<OrderAssignment> findByDeliveryProfileAndStatusIn(DeliveryProfile deliveryProfile, Collection<AssignmentStatus> statuses, Sort sort);

    /**
     * Finds an order assignment by the external order ID.
     * Assuming orderId is unique per assignment as per index `idx_orderassignment_orderid`.
     *
     * @param orderId The UUID of the order from the Order Service.
     * @return An {@link Optional} containing the {@link OrderAssignment} if found.
     */
    Optional<OrderAssignment> findByOrderId(UUID orderId);

    /**
     * Finds all active assignments (not COMPLETED, FAILED, CANCELLED) for a delivery profile.
     *
     * @param deliveryProfile The delivery profile.
     * @param activeStatuses A collection of statuses considered active.
     * @param sort Sorting criteria.
     * @return A list of active {@link OrderAssignment}.
     */
    List<OrderAssignment> findByDeliveryProfileAndStatusInAndDeliveryProfileActiveTrue(
            DeliveryProfile deliveryProfile,
            Collection<AssignmentStatus> activeStatuses,
            Sort sort
    );
    // Alternative for above if DeliveryProfile active status is checked in service:
    // List<OrderAssignment> findByDeliveryProfileAndStatusIn(DeliveryProfile deliveryProfile, Collection<AssignmentStatus> activeStatuses, Sort sort);


}
