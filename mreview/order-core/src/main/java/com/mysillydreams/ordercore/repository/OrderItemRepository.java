package com.mysillydreams.ordercore.repository;

import com.mysillydreams.ordercore.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
    // Custom query methods can be added here.
    // For example:
    // List<OrderItem> findByOrderId(UUID orderId);
    // List<OrderItem> findByProductSku(String productSku);
}
