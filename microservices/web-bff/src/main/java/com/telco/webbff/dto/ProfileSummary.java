package com.telco.webbff.dto;

/**
 * UI-shaped view of the caller's customer profile, composed from customer-service (ADR-022). Carries
 * only the fields the customer read model actually exposes for display - the customer id, the full
 * name, and the account status. PII (the identity number) is masked and never surfaced here.
 */
public record ProfileSummary(
        String customerId,
        String fullName,
        String status) {
}
