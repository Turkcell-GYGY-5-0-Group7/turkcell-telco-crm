package com.telco.webbff.dto;

import java.math.BigDecimal;

/**
 * UI-shaped tariff option for the onboarding wizard, composed from product-catalog-service.
 */
public record TariffOption(
        String code,
        String name,
        String description,
        BigDecimal monthlyPrice,
        String currency) {
}
