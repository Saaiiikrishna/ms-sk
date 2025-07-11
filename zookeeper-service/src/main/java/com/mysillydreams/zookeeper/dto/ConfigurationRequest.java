package com.mysillydreams.zookeeper.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Configuration request DTO
 */
public class ConfigurationRequest {
    
    @NotBlank(message = "Key is required")
    private String key;
    
    @NotBlank(message = "Value is required")
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
