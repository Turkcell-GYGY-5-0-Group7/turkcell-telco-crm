package com.telco.order.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * Lightweight DTO for the subscription-service {@code GET /internal/subscriptions/{id}} response
 * body (Sprint 24 Feature 24.2). Only the fields the order validation matrix needs are mapped;
 * unknown fields are ignored for forward-compatibility (ADR-019).
 *
 * <p>{@code customerId} + {@code status} back the ownership/ACTIVE checks on ADDON and PLAN_CHANGE
 * orders; {@code tariffCode} backs the PLAN_CHANGE "new tariff must differ" rule (design-note D2).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SubscriptionClientResponse(
        UUID id,
        UUID customerId,
        String status,
        String tariffCode,
        Integer tariffVersion,
        String msisdn
) {
}
