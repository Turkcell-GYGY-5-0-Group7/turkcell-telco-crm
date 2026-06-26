package com.telco.identity.api;

import com.telco.platform.common.api.ApiError;
import com.telco.platform.common.api.ApiMeta;
import com.telco.platform.common.api.ApiResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * Maps Spring Security's {@link AccessDeniedException} (thrown by {@code @PreAuthorize}) to 403.
 * Spring MVC's {@code @ControllerAdvice} processes this before the catch-all handler in the
 * platform's {@code GlobalExceptionHandler}, keeping the HTTP contract correct (ADR-015).
 */
@RestControllerAdvice
class SecurityExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResult<Object>> handleAccessDenied(AccessDeniedException ex,
                                                          HttpServletRequest request) {
        ApiError error = new ApiError("ACCESS_DENIED", "Access denied", null, null);
        ApiMeta meta = new ApiMeta(null, null, Instant.now(), "identity-service",
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResult.failure(error, meta));
    }
}
