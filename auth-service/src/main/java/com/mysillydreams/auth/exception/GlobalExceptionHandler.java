package com.mysillydreams.auth.exception;

import com.mysillydreams.auth.exception.MfaAuthenticationRequiredException;
import io.jsonwebtoken.JwtException;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.NotFoundException; // JAX-RS NotFoundException, as thrown by Keycloak admin client or our service
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String ERROR_KEY = "error";
    private static final String MESSAGE_KEY = "message";

    /**
     * Enhanced error response structure
     */
    public static class ErrorResponse {
        private String error;
        private String message;
        private int status;
        private LocalDateTime timestamp;
        private String path;
        private Map<String, Object> details;

        public ErrorResponse(String error, String message, int status, String path) {
            this.error = error;
            this.message = message;
            this.status = status;
            this.timestamp = LocalDateTime.now();
            this.path = path;
            this.details = new HashMap<>();
        }

        // Getters and setters
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public int getStatus() { return status; }
        public void setStatus(int status) { this.status = status; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public Map<String, Object> getDetails() { return details; }
        public void setDetails(Map<String, Object> details) { this.details = details; }
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", System.currentTimeMillis());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        // Collect all field errors
        String errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        body.put(ERROR_KEY, "Validation failed: " + errors);
        logger.warn("Validation failed for request {}: {}", request.getDescription(false), errors, ex);
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class) // For @Validated on params/path variables
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", System.currentTimeMillis());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        String errors = ex.getConstraintViolations()
                .stream()
                .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                .collect(Collectors.joining(", "));
        body.put(ERROR_KEY, "Constraint violation: " + errors);
        logger.warn("Constraint violation for request {}: {}", request.getDescription(false), errors, ex);
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex, WebRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", System.currentTimeMillis());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put(ERROR_KEY, ex.getMessage());
        logger.warn("Missing request parameter for request {}: {}", request.getDescription(false), ex.getMessage(), ex);
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }


    @ExceptionHandler(MfaAuthenticationRequiredException.class)
    public ResponseEntity<Object> handleMfaAuthenticationRequired(MfaAuthenticationRequiredException ex, WebRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put(ERROR_KEY, "MFA authentication required");
        body.put("mfaRequired", true);
        body.put(MESSAGE_KEY, ex.getMessage());
        logger.warn("MFA authentication required for request {}: {}", request.getDescription(false), ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Object> handleBadCredentials(BadCredentialsException ex, WebRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put(ERROR_KEY, "Invalid credentials");
        // Already logged with IP in AuthController, general log here
        logger.warn("BadCredentialsException for request {}: {}", request.getDescription(false), ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AuthenticationException.class) // Catches other Spring Security auth exceptions
    public ResponseEntity<Object> handleAuthenticationException(AuthenticationException ex, WebRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put(ERROR_KEY, "Authentication failed");
        logger.warn("AuthenticationException for request {}: {}", request.getDescription(false), ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put(ERROR_KEY, "Access denied");
        logger.warn("AccessDeniedException for request {}: {}", request.getDescription(false), ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(NotFoundException.class) // JAX-RS NotFound, e.g., from Keycloak client or our service
    public ResponseEntity<Object> handleJaxRsNotFound(NotFoundException ex, WebRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put(ERROR_KEY, ex.getMessage() != null ? ex.getMessage() : "Resource not found");
        logger.warn("NotFoundException (JAX-RS) for request {}: {}", request.getDescription(false), ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }

    // Custom application specific exception (example)
    // @ExceptionHandler(MyCustomApplicationException.class)
    // public ResponseEntity<Object> handleMyCustomApplicationException(MyCustomApplicationException ex, WebRequest request) {
    //     logger.error("CustomApplicationException for request {}: {}", request.getDescription(false), ex.getMessage(), ex);
    //     Map<String, Object> body = new HashMap<>();
    //     body.put(ERROR_KEY, ex.getMessage()); // Or a more generic error
    //     return new ResponseEntity<>(body, ex.getHttpStatus()); // Assuming exception carries HttpStatus
    // }


    /**
     * Handle MFA authentication required
     */
    @ExceptionHandler(MfaAuthenticationRequiredException.class)
    public ResponseEntity<ErrorResponse> handleMfaAuthenticationRequiredException(
            MfaAuthenticationRequiredException ex, WebRequest request) {
        logger.info("MFA authentication required: {}", ex.getMessage());

        ErrorResponse error = new ErrorResponse(
            "MFA_REQUIRED",
            ex.getMessage(),
            HttpStatus.UNAUTHORIZED.value(),
            request.getDescription(false).replace("uri=", "")
        );
        error.getDetails().put("mfaRequired", true);

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    /**
     * Handle JWT exceptions
     */
    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ErrorResponse> handleJwtException(
            JwtException ex, WebRequest request) {
        logger.warn("JWT error: {}", ex.getMessage());

        ErrorResponse error = new ErrorResponse(
            "INVALID_TOKEN",
            "Invalid or expired token.",
            HttpStatus.UNAUTHORIZED.value(),
            request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    /**
     * Handle entity not found exceptions
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFoundException(
            EntityNotFoundException ex, WebRequest request) {
        logger.warn("Entity not found: {}", ex.getMessage());

        ErrorResponse error = new ErrorResponse(
            "ENTITY_NOT_FOUND",
            ex.getMessage(),
            HttpStatus.NOT_FOUND.value(),
            request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Handle response status exceptions
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(
            ResponseStatusException ex, WebRequest request) {
        logger.warn("Response status exception: {}", ex.getMessage());

        ErrorResponse error = new ErrorResponse(
            ex.getStatusCode().toString(),
            ex.getReason() != null ? ex.getReason() : "An error occurred",
            ex.getStatusCode().value(),
            request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @ExceptionHandler(Exception.class) // Generic fallback for any other exceptions
    public ResponseEntity<ErrorResponse> handleAllOtherExceptions(Exception ex, WebRequest request) {
        logger.error("Unhandled exception for request {}: {}", request.getDescription(false), ex.getMessage(), ex);

        ErrorResponse error = new ErrorResponse(
            "INTERNAL_SERVER_ERROR",
            "An unexpected error occurred. Please try again later.",
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
