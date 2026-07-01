package com.telco.platform.starter.api;

import com.telco.platform.common.api.ApiError;
import com.telco.platform.common.api.ApiMeta;
import com.telco.platform.common.api.ApiResult;
import com.telco.platform.common.exception.AccessDeniedException;
import com.telco.platform.common.exception.BusinessRuleException;
import com.telco.platform.common.exception.CommonErrorCode;
import com.telco.platform.common.exception.ConflictException;
import com.telco.platform.common.exception.DependencyFailureException;
import com.telco.platform.common.exception.PlatformException;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.common.exception.UnauthenticatedException;
import com.telco.platform.common.exception.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Translates exceptions into the standard {@link ApiResult} error contract (ADR-015).
 *
 * <p>Each {@link PlatformException} subtype maps to a specific HTTP status; Spring validation
 * failures map to 400; anything else maps to 500 {@code INTERNAL_ERROR}. Stack traces are never
 * exposed in the response body. Every response carries {@link ApiMeta} populated from the active
 * correlation context.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ApiMetaFactory metaFactory;
    private final ExceptionLogRecorder logRecorder;

    public GlobalExceptionHandler(ApiMetaFactory metaFactory, ExceptionLogRecorder logRecorder) {
        this.metaFactory = metaFactory;
        this.logRecorder = logRecorder;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResult<Object>> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex, request);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResult<Object>> handleValidation(ValidationException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex, request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResult<Object>> handleConflict(ConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex, request);
    }

    @ExceptionHandler(UnauthenticatedException.class)
    public ResponseEntity<ApiResult<Object>> handleUnauthenticated(UnauthenticatedException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResult<Object>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex, request);
    }

    // Spring Security's own AccessDeniedException (thrown by @PreAuthorize) has a different type
    // from the platform's AccessDeniedException. Without this handler it falls through to
    // handleUnexpected and returns 500 instead of 403.
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiResult<Object>> handleSecurityAccessDenied(
            org.springframework.security.access.AccessDeniedException ex, HttpServletRequest request) {
        String logId = logRecorder.record(ex, request.getRequestURI(), HttpStatus.FORBIDDEN.value(),
                CommonErrorCode.ACCESS_DENIED.code());
        ApiError error = new ApiError(CommonErrorCode.ACCESS_DENIED.code(), "Access denied",
                null, metaFactory.traceId(), logId);
        return respond(HttpStatus.FORBIDDEN, error, request);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiResult<Object>> handleBusinessRule(BusinessRuleException ex, HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex, request);
    }

    @ExceptionHandler(DependencyFailureException.class)
    public ResponseEntity<ApiResult<Object>> handleDependencyFailure(DependencyFailureException ex, HttpServletRequest request) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Object>> handleBeanValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            details.put(error.getField(), error.getDefaultMessage());
        }
        String logId = logRecorder.record(ex, request.getRequestURI(), HttpStatus.BAD_REQUEST.value(),
                CommonErrorCode.VALIDATION_FAILED.code());
        ApiError error = new ApiError(CommonErrorCode.VALIDATION_FAILED.code(), "Validation failed",
                details, metaFactory.traceId(), logId);
        return respond(HttpStatus.BAD_REQUEST, error, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResult<Object>> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        ex.getConstraintViolations().forEach(v -> details.put(String.valueOf(v.getPropertyPath()), v.getMessage()));
        String logId = logRecorder.record(ex, request.getRequestURI(), HttpStatus.BAD_REQUEST.value(),
                CommonErrorCode.VALIDATION_FAILED.code());
        ApiError error = new ApiError(CommonErrorCode.VALIDATION_FAILED.code(), "Validation failed",
                details, metaFactory.traceId(), logId);
        return respond(HttpStatus.BAD_REQUEST, error, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Object>> handleUnexpected(Exception ex, HttpServletRequest request) {
        LOGGER.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), ex);
        String logId = logRecorder.record(ex, request.getRequestURI(), HttpStatus.INTERNAL_SERVER_ERROR.value(),
                CommonErrorCode.INTERNAL_ERROR.code());
        ApiError error = new ApiError(CommonErrorCode.INTERNAL_ERROR.code(),
                "An unexpected error occurred", null, metaFactory.traceId(), logId);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, error, request);
    }

    private ResponseEntity<ApiResult<Object>> build(HttpStatus status, PlatformException ex, HttpServletRequest request) {
        if (status.is5xxServerError()) {
            LOGGER.error("Platform exception ({}): {}", ex.code().code(), ex.getMessage(), ex);
        } else {
            LOGGER.debug("Platform exception ({}): {}", ex.code().code(), ex.getMessage());
        }
        String logId = logRecorder.record(ex, request.getRequestURI(), status.value(), ex.code().code());
        ApiError error = new ApiError(ex.code().code(), ex.getMessage(), ex.details(), metaFactory.traceId(), logId);
        return respond(status, error, request);
    }

    private ResponseEntity<ApiResult<Object>> respond(HttpStatus status, ApiError error, HttpServletRequest request) {
        ApiMeta meta = metaFactory.create(request.getRequestURI());
        return ResponseEntity.status(status).body(ApiResult.failure(error, meta));
    }
}
