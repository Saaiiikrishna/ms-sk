package com.mysillydreams.auth.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.NotFoundException; // JAX-RS NotFoundException, as thrown by Keycloak admin client or our service
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String ERROR_KEY = "error";
    private static final String MESSAGE_KEY = "message"; // For more detailed messages if needed, use with caution

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


    @ExceptionHandler(Exception.class) // Generic fallback for any other exceptions
    public ResponseEntity<Object> handleAllOtherExceptions(Exception ex, WebRequest request) {
        logger.error("Unhandled exception for request {}: {}", request.getDescription(false), ex.getMessage(), ex);
        Map<String, Object> body = new HashMap<>();
        body.put(ERROR_KEY, "An unexpected internal error occurred. Please try again later.");
        // In dev/test, you might want to return more details: body.put("details", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
