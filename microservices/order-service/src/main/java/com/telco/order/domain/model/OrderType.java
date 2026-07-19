package com.telco.order.domain.model;

/**
 * The kind of order, derived from its items at creation time and persisted so saga consumers can
 * branch without re-deriving (Sprint 24 Feature 24.2, design-note D1/D2).
 *
 * <ul>
 *   <li>{@link #NEW_LINE} - onboarding: exactly one TARIFF item plus 0..N bundled ADDON items.</li>
 *   <li>{@link #ADDON} - standalone addon purchase: 1..N ADDON items, all targeting the same
 *       existing ACTIVE subscription.</li>
 *   <li>{@link #PLAN_CHANGE} - tariff change on an existing ACTIVE subscription: exactly one
 *       TARIFF item carrying a {@code targetSubscriptionId}.</li>
 * </ul>
 */
public enum OrderType {
    NEW_LINE,
    ADDON,
    PLAN_CHANGE
}
