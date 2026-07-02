package com.telco.subscription.domain;

/**
 * Lifecycle state of a {@link SimCard} physical card.
 *
 * <p>AVAILABLE: in inventory, ready for assignment. ASSIGNED: linked to an active subscription.
 * SUSPENDED: temporarily disabled (e.g. subscription suspended). DECOMMISSIONED: permanently retired.
 */
public enum SimCardStatus {
    AVAILABLE,
    ASSIGNED,
    SUSPENDED,
    DECOMMISSIONED
}
