package com.mysillydreams.delivery.repository;

import com.mysillydreams.delivery.domain.DeliveryProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeliveryProfileRepository extends JpaRepository<DeliveryProfile, UUID> {
    // Example custom queries:
    // List<DeliveryProfile> findByStatus(String status);
    // List<DeliveryProfile> findByCurrentLatitudeBetweenAndCurrentLongitudeBetween(
    //    Double minLat, Double maxLat, Double minLon, Double maxLon);
}
