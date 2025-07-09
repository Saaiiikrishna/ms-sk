package com.ecommerce.vendorfulfillmentservice.repository;

import com.ecommerce.vendorfulfillmentservice.entity.VendorOrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface VendorOrderStatusHistoryRepository extends JpaRepository<VendorOrderStatusHistory, UUID> {
    // Custom query methods can be added here if needed
}
