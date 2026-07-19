package com.telco.order.domain.model;

/**
 * Discriminates the two kinds of order line items (Sprint 24 Feature 24.2, design-note D1).
 *
 * <ul>
 *   <li>{@link #TARIFF} - a tariff line carrying the V5 tariff snapshot
 *       (tariffId/tariffCode/tariffVersion) and, optionally, a campaign discount.</li>
 *   <li>{@link #ADDON} - an addon line carrying a catalog {@code productCode} plus an allowance
 *       snapshot taken at order-creation time; never campaign-discounted (campaign eligibility is
 *       tariff-scoped, ADR-027).</li>
 * </ul>
 */
public enum OrderItemType {
    TARIFF,
    ADDON
}
