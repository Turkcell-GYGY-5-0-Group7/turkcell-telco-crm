package com.telco.subscription.application.dto;

/**
 * Request body for the manual suspend endpoint. {@code reason} is optional; when absent the handler
 * defaults to {@code MANUAL}. Carried into {@code subscription.suspended.v1} for downstream dunning.
 * MUST NOT contain PII.
 */
public record SuspendSubscriptionRequest(
        String reason
) {
}
