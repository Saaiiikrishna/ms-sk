package com.mysillydreams.vendor.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resourceName, UUID id) {
        super(String.format("%s not found with id: %s", resourceName, id.toString()));
    }

    public ResourceNotFoundException(String resourceName, String attributeName, String attributeValue) {
        super(String.format("%s not found with %s: %s", resourceName, attributeName, attributeValue));
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
