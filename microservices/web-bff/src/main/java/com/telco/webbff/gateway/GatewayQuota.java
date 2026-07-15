package com.telco.webbff.gateway;

import java.util.UUID;

/**
 * Partial view of usage-service's quota read model, deserialized from the gateway response
 * ({@code /api/v1/usage/subscriptions/{id}/quota}). Carries the period totals and remaining balances
 * per meter (minutes, SMS, data MB); web-bff derives "used = total - remaining" to shape the account
 * usage gauge. Unknown properties (quota id, period bounds) are ignored. Local DTO; no cross-service
 * coupling (ADR-022).
 */
public record GatewayQuota(
        UUID subscriptionId,
        long minutesTotal,
        long smsTotal,
        long mbTotal,
        long minutesRemaining,
        long smsRemaining,
        long mbRemaining) {
}
