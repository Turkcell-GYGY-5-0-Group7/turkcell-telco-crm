package com.telco.usage.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates Spring Security's {@link AccessDeniedException} to HTTP 403.
 *
 * <p>The platform {@code GlobalExceptionHandler} handles
 * {@code com.telco.platform.common.exception.AccessDeniedException} (platform type), but NOT
 * Spring Security's {@code org.springframework.security.access.AccessDeniedException} thrown by
 * {@code @PreAuthorize}. Without this advice, those failures fall through to the catch-all
 * {@code handleUnexpected} and become 500.
 */
@RestControllerAdvice
class UsageAccessDeniedAdvice {

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Void> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
}
