package com.telco.fraud.domain;

/**
 * The kind of subscription-service lifecycle event captured in a {@link MsisdnLifecycleSignal} row
 * (design-note.md Section 6). Each value maps 1:1 to a consumed domain event (Feature 23.2):
 * {@code MSISDN_ALLOCATED} &lt;- {@code msisdn.allocated.v1}, {@code MSISDN_RELEASED} &lt;-
 * {@code msisdn.released.v1}, {@code SUBSCRIPTION_SUSPENDED} &lt;- {@code subscription.suspended.v1},
 * {@code SUBSCRIPTION_ACTIVATED} &lt;- {@code subscription.activated.v1}. This enum only names the
 * values; consumption and mapping are Feature 23.2 work.
 */
public enum MsisdnLifecycleEventType {
    MSISDN_ALLOCATED,
    MSISDN_RELEASED,
    SUBSCRIPTION_SUSPENDED,
    SUBSCRIPTION_ACTIVATED
}
