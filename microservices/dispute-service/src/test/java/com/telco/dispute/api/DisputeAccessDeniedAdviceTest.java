package com.telco.dispute.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

class DisputeAccessDeniedAdviceTest {

    @Test
    void translates_spring_security_access_denied_to_403() {
        DisputeAccessDeniedAdvice advice = new DisputeAccessDeniedAdvice();

        ResponseEntity<Void> response = advice.handleAccessDenied(new AccessDeniedException("denied"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
