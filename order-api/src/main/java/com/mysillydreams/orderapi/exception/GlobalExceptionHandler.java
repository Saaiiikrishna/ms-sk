package com.mysillydreams.orderapi.exception;

import com.mysillydreams.orderapi.dto.ApiError;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.kafka.KafkaException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest; // Correct import for HttpServletRequest
import jakarta.validation.ConstraintViolationException; // Correct import for ConstraintViolationException
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final MeterRegistry meterRegistry;

    public GlobalExceptionHandler(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    private ResponseEntity<ApiError> buildErrorResponse(HttpStatus status, String errorCode, String message, HttpServletRequest request, Exception ex) {
        log.error("{} at path {}: {} (Error Code: {})", ex.getClass().getSimpleName(), request.getRequestURI(), message, errorCode, ex);

        Counter.builder("orderapi.errors")
            .tag("exception", ex.getClass().getSimpleName())
            .tag("path", request.getRequestURI())
            .tag("status", String.valueOf(status.value()))
            .description("Counts occurrences of specific errors")
            .register(meterRegistry)
            .increment();

        ApiError apiError = new ApiError(status.value(), errorCode, message, request.getRequestURI());
        return new ResponseEntity<>(apiError, status);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> "'" + error.getField() + "': " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, request, ex);
    }

    @ExceptionHandler(ConstraintViolationException.class) // For @Validated path variables, request params
    public ResponseEntity<ApiError> handleConstraintViolationException(ConstraintViolationException ex, HttpServletRequest request) {
        String message = ex.getConstraintViolations().stream()
                .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                .collect(Collectors.joining(", "));
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, request, ex);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingRequestHeaderException(MissingRequestHeaderException ex, HttpServletRequest request) {
        String message = "Required request header '" + ex.getHeaderName() + "' is not present.";
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "MISSING_HEADER", message, request, ex);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        // Provide a generic message or try to extract more specific info if safe
        String message = "Malformed JSON request or invalid request body format.";
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", message, request, ex);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = String.format("Parameter '%s' with value '%s' could not be converted to type '%s'.",
                                       ex.getName(), ex.getValue(), ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "TYPE_MISMATCH", message, request, ex);
    }

    @ExceptionHandler(KafkaException.class) // This is a broad Spring Kafka exception
    public ResponseEntity<ApiError> handleKafkaException(KafkaException ex, HttpServletRequest request) {
        // For Kafka issues, it's often a server-side problem or misconfiguration affecting availability
        return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, "KAFKA_ERROR", "Error communicating with Kafka: " + ex.getLocalizedMessage(), request, ex);
    }

    // A more specific Kafka exception if needed, e.g., for producer specific issues
    // @ExceptionHandler(org.apache.kafka.common.errors.TimeoutException.class) // Example
    // public ResponseEntity<ApiError> handleKafkaTimeoutException(org.apache.kafka.common.errors.TimeoutException ex, HttpServletRequest request) {
    //     return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, "KAFKA_TIMEOUT", "Kafka operation timed out: " + ex.getLocalizedMessage(), request, ex);
    // }

    // Fallback for any other unhandled exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGenericException(Exception ex, HttpServletRequest request) {
        // Log the full stack trace for unexpected errors
        log.error("Unhandled exception at path {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "An unexpected error occurred: " + ex.getLocalizedMessage(), request, ex);
    }
}
