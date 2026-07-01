package com.telco.subscription.domain;

/** Lifecycle states of a {@link Subscription} (state machine enforced in the aggregate). FR-14. */
public enum SubscriptionStatus {
    ACTIVE,
    SUSPENDED,
    TERMINATED
}
