package com.mysillydreams.orderapi.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

// Using a record for a concise, immutable DTO
public record ApiError(
    int status,         // HTTP status code
    String error,       // A brief, machine-readable error code or type (e.g., VALIDATION_FAILED)
    String message,     // User-friendly error message
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    Instant timestamp,  // Timestamp of the error
    String path         // Request path where the error occurred
) {
    public ApiError(int status, String error, String message, String path) {
        this(status, error, message, Instant.now(), path);
    }
}
