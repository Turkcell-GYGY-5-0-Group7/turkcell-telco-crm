package com.telco.fraud.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the RBAC 403 behavior: a Spring Security {@code AccessDeniedException} (raised when a
 * caller lacking the required role hits a {@code @PreAuthorize}-gated fraud route) is translated to
 * HTTP 403, not the platform catch-all 500. Mirrors campaign-service's access-denied advice fix
 * ({@code docs/tasks/lessons.md} 2026-06-26).
 */
class FraudAccessDeniedAdviceTest {

    @Test
    void access_denied_is_translated_to_403() {
        FraudAccessDeniedAdvice advice = new FraudAccessDeniedAdvice();

        ResponseEntity<Void> response =
                advice.handleAccessDenied(new AccessDeniedException("forbidden"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
