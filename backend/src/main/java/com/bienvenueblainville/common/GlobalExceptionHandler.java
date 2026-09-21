package com.bienvenueblainville.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(ApiError.of(400, "Validation failed", fieldErrors));
    }

    /**
     * Violations on query parameters, which arrive by a different route.
     *
     * <p>{@code @Valid} on a request body produces MethodArgumentNotValidException
     * and was handled above; {@code @Min}/{@code @Max} on a {@code @RequestParam}
     * of a {@code @Validated} controller produces this instead, and without a
     * handler it left the filter chain as a 500. A caller asking for
     * {@code ?size=100000} was told the server had broken rather than that
     * they had asked for too much - and the same was true of
     * {@code /api/collections/upcoming?days=999}, which has had that bound
     * since long before paging existed.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleParameterValidation(ConstraintViolationException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            // "list.size" -> "size": the method name is an implementation
            // detail the caller cannot act on.
            String path = violation.getPropertyPath().toString();
            fieldErrors.put(path.substring(path.lastIndexOf('.') + 1), violation.getMessage());
        }
        return ResponseEntity.badRequest().body(ApiError.of(400, "Validation failed", fieldErrors));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException ex) {
        // Carry the exception's own headers through. Without this, anything a
        // ResponseStatusException says in a header is silently dropped here -
        // which is how a Retry-After that told the browser a photo was still
        // being prepared never reached it.
        return ResponseEntity.status(ex.getStatusCode())
                .headers(ex.getResponseHeaders())
                .body(ApiError.of(ex.getStatusCode().value(), ex.getReason()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(401, "Authentication required"));
    }
}
