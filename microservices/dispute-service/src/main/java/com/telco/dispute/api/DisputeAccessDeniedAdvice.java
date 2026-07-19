package com.telco.dispute.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates Spring Security's AccessDeniedException to 403.
 *
 * The platform GlobalExceptionHandler has no handler for org.springframework.security's
 * AccessDeniedException (only for com.telco.platform.common.exception.AccessDeniedException),
 * so @PreAuthorize failures fall through to handleUnexpected(Exception) and become 500.
 * Spring MVC picks the most specific @ExceptionHandler, so this class wins over the fallback.
 */
@RestControllerAdvice
class DisputeAccessDeniedAdvice {

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Void> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
}
