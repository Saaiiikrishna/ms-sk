package com.mysillydreams.ordercore.repository;

import com.mysillydreams.ordercore.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    // Basic CRUD methods are provided by JpaRepository.
    // Custom query methods can be added here if needed.
    // For example:
    // Optional<Order> findByCustomerIdAndId(UUID customerId, UUID orderId);
    // List<Order> findByCurrentStatus(OrderStatus status);
}
