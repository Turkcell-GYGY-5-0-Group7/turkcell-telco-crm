package com.telco.webbff.dto;

/**
 * Result of {@code POST /bff/v1/onboarding/order}. Carries the placed order's id and status plus the
 * {@code Idempotency-Key} the client supplied (echoed so the UI can correlate replays). UI DTO, not
 * {@code ApiResult<T>} (ADR-015 BFF exception). Real order placement is composed in 16.4.
 */
public record OnboardingOrderResponse(
        String orderId,
        String status,
        String idempotencyKey) {
}
