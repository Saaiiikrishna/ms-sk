package com.mysillydreams.inventoryapi.service;

import com.mysillydreams.inventoryapi.dto.AdjustStockRequest;
import com.mysillydreams.inventoryapi.dto.ReservationRequestDto;
import com.mysillydreams.inventoryapi.dto.StockLevelDto;
// Removed ResourceNotFoundException as getStock now returns a default StockLevel
import com.mysillydreams.inventoryapi.domain.StockLevel;
import com.mysillydreams.inventoryapi.repository.StockLevelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
// Removed List, Map, Collectors as they are no longer used here

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService { // Implements the new interface
    private final StockLevelRepository repo;
    private final KafkaTemplate<String, Object> kafka;

    @Value("${kafka.topics.reservationRequested}") // Corrected property name from application.yml
    private String reservationTopic;

    @Override
    @Transactional(readOnly = true) // As per new spec
    public StockLevelDto getStock(String sku) {
        StockLevel lvl = repo.findById(sku)
                .orElse(new StockLevel(sku, 0, 0, Instant.now())); // Return default if not found
        return StockLevelDto.from(lvl); // DTO.from method is updated to not expect/use updatedAt
    }

    @Override
    @Transactional // Not readOnly
    public void adjustStock(AdjustStockRequest req) {
        StockLevel lvl = repo.findById(req.getSku())
                .orElse(new StockLevel(req.getSku(), 0, 0, Instant.now())); // Create if not found

        // Add sophisticated logic for stock adjustment if needed (e.g. check for negative stock not allowed unless specified)
        // For now, simple addition as per delta
        int newAvailable = lvl.getAvailable() + req.getDelta();
        // if (newAvailable < 0) {
        //     throw new IllegalArgumentException("Adjusting stock for SKU " + req.getSku() + " by " + req.getDelta() + " would result in negative stock.");
        // }
        lvl.setAvailable(newAvailable);
        // lvl.setUpdatedAt(Instant.now()); // This will be handled by @UpdateTimestamp
        repo.save(lvl);
        // Kafka message sending removed from this method as per new spec
    }

    @Override
    public void reserve(ReservationRequestDto req) { // Method name changed from publishReservation
        // The entire ReservationRequestDto `req` is sent as the message value.
        // The key is `req.getOrderId().toString()`.
        kafka.send(reservationTopic, req.getOrderId().toString(), req);
    }
}
