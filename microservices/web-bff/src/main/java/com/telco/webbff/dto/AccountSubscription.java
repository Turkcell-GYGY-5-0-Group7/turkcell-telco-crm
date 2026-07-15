package com.telco.webbff.dto;

/**
 * One row of the account view: a subscription paired with its current usage/quota roll-up. Usage is
 * populated only for ACTIVE subscriptions (usage-service provisions a quota on activation); it is
 * {@code null} for suspended or terminated subscriptions, which carry no live quota.
 */
public record AccountSubscription(
        SubscriptionSummary subscription,
        UsageSummary usage) {
}
