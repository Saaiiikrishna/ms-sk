package com.mysillydreams.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin; // For latitude/longitude validation
import jakarta.validation.constraints.DecimalMax;

import java.time.Instant;
import java.util.UUID; // Though assignmentId is path variable, DTO might carry it if sent to Kafka from elsewhere

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GpsUpdateDto {

    // assignmentId might not be in the request body if it's a path variable,
    // but it's good for the Kafka event schema.
    // private UUID assignmentId;

    @NotNull
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private Double latitude;

    @NotNull
    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private Double longitude;

    @NotNull
    private Instant timestamp; // When the GPS reading was taken

    private Double accuracy; // Optional: GPS accuracy in meters
    private Double speed;    // Optional: Speed in m/s
    private Double heading;  // Optional: Heading/course in degrees
}
