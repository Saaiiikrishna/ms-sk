package com.ecommerce.vendorfulfillmentservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class AssignmentNotFoundException extends RuntimeException {
    public AssignmentNotFoundException(UUID assignmentId) {
        super("VendorOrderAssignment not found with ID: " + assignmentId);
    }
}
