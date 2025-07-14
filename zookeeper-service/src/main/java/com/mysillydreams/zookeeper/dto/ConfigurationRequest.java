package com.mysillydreams.zookeeper.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Production-ready configuration request DTO with comprehensive validation
 */
public class ConfigurationRequest {

    @NotBlank(message = "Key is required")
    @Size(min = 1, max = 200, message = "Key must be between 1 and 200 characters")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Key can only contain alphanumeric characters, dots, hyphens, and underscores")
    private String key;

    @NotBlank(message = "Value is required")
    @Size(min = 1, max = 10000, message = "Value must be between 1 and 10000 characters")
    private String value;
    
    public ConfigurationRequest() {}
    
    public ConfigurationRequest(String key, String value) {
        this.key = key;
        this.value = value;
    }
    
    public String getKey() {
        return key;
    }
    
    public void setKey(String key) {
        this.key = key;
    }
    
    public String getValue() {
        return value;
    }
    
    public void setValue(String value) {
        this.value = value;
    }
}
