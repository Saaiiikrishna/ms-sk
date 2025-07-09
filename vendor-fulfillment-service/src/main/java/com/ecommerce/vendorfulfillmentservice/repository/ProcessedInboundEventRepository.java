package com.ecommerce.vendorfulfillmentservice.repository;

import com.ecommerce.vendorfulfillmentservice.entity.ProcessedInboundEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedInboundEventRepository extends JpaRepository<ProcessedInboundEvent, String> {
    // Spring Data JPA will provide findById(eventId) which is existsById(eventId) effectively
    boolean existsByEventIdAndConsumerGroup(String eventId, String consumerGroup);
}
