package com.telco.campaign.domain.model;

/**
 * Lifecycle status of a {@link CampaignRedemption} (design-note.md Section 5, ADR-027 Decision
 * Section 4). Driven by consuming {@code order.created.v1} (RESERVED), {@code payment.completed.v1}
 * (CONFIRMED), and {@code order.cancelled.v1} (RELEASED) - wired in Feature 21.4.
 */
public enum RedemptionStatus {
    RESERVED,
    CONFIRMED,
    RELEASED
}
