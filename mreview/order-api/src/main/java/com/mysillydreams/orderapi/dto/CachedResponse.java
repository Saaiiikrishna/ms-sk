package com.mysillydreams.orderapi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpHeaders; // Using Spring's HttpHeaders
import org.springframework.http.HttpStatus;   // Using Spring's HttpStatus

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CachedResponse implements Serializable {
    private static final long serialVersionUID = 1L; // Good practice for Serializable classes

    private int statusCode; // Store as int for broader compatibility
    private Map<String, List<String>> headers; // Store headers as a map
    private byte[] body; // Store body as byte array for flexibility

    // Helper to convert Spring HttpStatus to int
    public void setHttpStatus(HttpStatus status) {
        this.statusCode = status.value();
    }

    // Helper to get Spring HttpStatus from int
    public HttpStatus getHttpStatus() {
        return HttpStatus.valueOf(this.statusCode);
    }

    // Helper to convert Spring HttpHeaders to Map
    public void setHttpHeaders(HttpHeaders httpHeaders) {
        this.headers = httpHeaders.toSingleValueMap().entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> List.of(e.getValue())));
        // A more direct way if multi-values per header are needed:
        // this.headers = new HashMap<>(httpHeaders);
    }

    // Helper to get Spring HttpHeaders from Map
    public HttpHeaders getHttpHeaders() {
        HttpHeaders httpHeaders = new HttpHeaders();
        if (this.headers != null) {
            this.headers.forEach((key, values) -> {
                values.forEach(value -> httpHeaders.add(key, value));
            });
        }
        return httpHeaders;
    }
}
