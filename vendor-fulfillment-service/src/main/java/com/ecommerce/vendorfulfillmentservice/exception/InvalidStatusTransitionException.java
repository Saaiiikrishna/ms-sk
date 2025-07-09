package com.ecommerce.vendorfulfillmentservice.exception;

import com.ecommerce.vendorfulfillmentservice.entity.AssignmentStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(UUID assignmentId, AssignmentStatus currentStatus, AssignmentStatus targetStatus) {
        super(String.format("Cannot transition VendorOrderAssignment %s from status %s to %s.",
                assignmentId, currentStatus, targetStatus));
    }

    public InvalidStatusTransitionException(String message) {
        super(message);
    }
}
