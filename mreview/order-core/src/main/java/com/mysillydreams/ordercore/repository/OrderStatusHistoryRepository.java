package com.mysillydreams.ordercore.repository;

import com.mysillydreams.ordercore.domain.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, UUID> {
    // Custom query methods can be added here.
    // For example:
    List<OrderStatusHistory> findByOrderIdOrderByTimestampDesc(UUID orderId);
}
