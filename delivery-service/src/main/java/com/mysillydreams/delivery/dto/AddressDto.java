package com.mysillydreams.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddressDto {
    // This DTO represents the structure expected in the JSONB fields in the database
    // and in Kafka messages for addresses.

    @NotBlank
    private String street;

    private String street2; // Optional, for apartment, suite, etc.

    @NotBlank
    private String city;

    @NotBlank
    private String stateOrProvince; // Or region

    @NotBlank
    private String postalCode;

    @NotBlank
    private String countryCode; // ISO 2-letter country code, e.g., "US", "CA"

    private Double latitude;    // Optional, for geolocation
    private Double longitude;   // Optional, for geolocation

    private String contactName; // Optional: Name of person at address
    private String contactPhone; // Optional: Phone number for person at address
    private String instructions; // Optional: Delivery instructions
}
