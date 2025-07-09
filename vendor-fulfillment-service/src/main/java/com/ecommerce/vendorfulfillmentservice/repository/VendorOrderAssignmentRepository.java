package com.ecommerce.vendorfulfillmentservice.repository;

import com.ecommerce.vendorfulfillmentservice.entity.VendorOrderAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.Optional;

@Repository
public interface VendorOrderAssignmentRepository extends JpaRepository<VendorOrderAssignment, UUID>, JpaSpecificationExecutor<VendorOrderAssignment> {
    Optional<VendorOrderAssignment> findByOrderId(UUID orderId);

    // To fetch assignment with history efficiently, if LAZY loading becomes an issue
    // @Query("SELECT va FROM VendorOrderAssignment va LEFT JOIN FETCH va.statusHistory WHERE va.id = :id")
    // Optional<VendorOrderAssignment> findByIdWithHistory(@Param("id") UUID id);
}
