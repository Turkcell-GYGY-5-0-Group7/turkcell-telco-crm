package com.telco.webbff.dto;

import java.math.BigDecimal;

/**
 * UI-shaped addon option for the onboarding wizard, composed from product-catalog-service. Bound to
 * the tariff it extends via {@code tariffCode}.
 */
public record AddonOption(
        String code,
        String name,
        String tariffCode,
        BigDecimal price,
        String currency) {
}
