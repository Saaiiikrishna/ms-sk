package com.mysillydreams.delivery.service;

import com.mysillydreams.delivery.dto.GpsUpdateDto; // This will become Avro GpsUpdateEvent
import com.mysillydreams.delivery.dto.PhotoOtpDto;
// Import Avro event for createAssignment input
import com.mysillydreams.delivery.dto.avro.ShipmentRequestedEvent;


import java.util.UUID;

public interface AssignmentService {

    /**
     * Creates a new delivery assignment based on a shipment request.
     * This involves selecting a courier (simplified for now), persisting the assignment,
     * and publishing a delivery.assignment.created event.
     *
     * @param event Avro event containing details from the order.shipment.requested event.
     * @return The UUID of the newly created delivery assignment.
     */
    UUID createAssignment(ShipmentRequestedEvent event);

    /**
     * Marks a delivery assignment as the courier having arrived at the pickup location.
     *
     * @param assignmentId The ID of the delivery assignment.
     */
    void markArrivedAtPickup(UUID assignmentId); // Renamed from markArrived for clarity

    /**
     * Marks a delivery assignment as picked up after verifying photo and/or OTP.
     * Publishes a delivery.picked_up event.
     *
     * @param assignmentId The ID of the delivery assignment.
     * @param photoOtpDto  DTO containing photo URL and/or OTP.
     */
    void markPickedUp(UUID assignmentId, PhotoOtpDto photoOtpDto);

    /**
     * Publishes a GPS update for a given assignment to Kafka.
     * This is typically a direct Kafka publish, not necessarily changing assignment status.
     *
     * @param assignmentId The ID of the delivery assignment.
     * @param gpsEvent Avro event containing latitude, longitude, timestamp.
     */
    void publishGpsUpdate(UUID assignmentId, com.mysillydreams.delivery.dto.avro.GpsUpdateEvent gpsEvent);

    /**
     * Marks a delivery assignment as the courier having arrived at the dropoff location.
     * (This state was missing in the original sketch but is a common one)
     * @param assignmentId The ID of the delivery assignment.
     */
    void markArrivedAtDropoff(UUID assignmentId);


    /**
     * Marks a delivery assignment as delivered after verifying photo and/or OTP.
     * Publishes a delivery.delivered event.
     *
     * @param assignmentId The ID of the delivery assignment.
     * @param photoOtpDto  DTO containing photo URL and/or OTP.
     */
    void markDelivered(UUID assignmentId, PhotoOtpDto photoOtpDto);

    /**
     * Handles a failed delivery attempt.
     * @param assignmentId The ID of the delivery assignment.
     * @param reason       Reason for failure.
     * @param notes        Courier notes.
     */
    void markDeliveryFailed(UUID assignmentId, String reason, String notes);

    /**
     * Cancels a delivery assignment.
     * @param assignmentId The ID of the delivery assignment.
     * @param reason       Reason for cancellation.
     * @param cancelledBy  Who initiated cancellation.
     */
    void cancelAssignment(UUID assignmentId, String reason, String cancelledBy);
}
