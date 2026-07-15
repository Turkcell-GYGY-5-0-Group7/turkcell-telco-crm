package com.telco.campaign.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates Spring Security's {@code AccessDeniedException} to 403.
 *
 * <p>The platform {@code GlobalExceptionHandler} has no handler for
 * {@code org.springframework.security.access.AccessDeniedException} (only for
 * {@code com.telco.platform.common.exception.AccessDeniedException}), so {@code @PreAuthorize}
 * failures would otherwise fall through to the catch-all and become 500 (mirrors
 * {@code CatalogAccessDeniedAdvice} in product-catalog-service; see
 * {@code docs/tasks/lessons.md} 2026-06-26).
 */
@RestControllerAdvice
class CampaignAccessDeniedAdvice {

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Void> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
}
