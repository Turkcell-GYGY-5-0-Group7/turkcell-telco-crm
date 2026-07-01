package com.telco.subscription.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates Spring Security's {@link AccessDeniedException} to 403.
 *
 * <p>The platform {@code GlobalExceptionHandler} only handles
 * {@code com.telco.platform.common.exception.AccessDeniedException} -> 403; a {@code @PreAuthorize}
 * failure throws {@code org.springframework.security.access.AccessDeniedException}, a different type,
 * which would otherwise fall through to {@code handleUnexpected(Exception)} and become a 500. Spring
 * MVC picks the most specific {@code @ExceptionHandler}, so this advice wins over that fallback
 * (lessons.md 2026-06-26).
 */
@RestControllerAdvice
class SubscriptionAccessDeniedAdvice {

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Void> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
}
