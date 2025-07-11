package com.mysillydreams.userservice.dto.delivery;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "Request payload containing GPS coordinates.")
public class GpsCoordinatesDto {

    @NotNull(message = "Latitude cannot be null.")
    @Min(value = -90, message = "Latitude must be between -90 and 90.")
    @Max(value = 90, message = "Latitude must be between -90 and 90.")
    @Schema(description = "GPS latitude.", example = "34.052235", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double latitude;

    @NotNull(message = "Longitude cannot be null.")
    @Min(value = -180, message = "Longitude must be between -180 and 180.")
    @Max(value = 180, message = "Longitude must be between -180 and 180.")
    @Schema(description = "GPS longitude.", example = "-118.243683", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double longitude;

    @Schema(description = "Optional: GPS accuracy in meters.", example = "5.0")
    private Double accuracy; // Optional
}
