package com.telco.campaign.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * JSON payload shape for {@code tariff.price-changed.v1} consumed from product-catalog-service's
 * {@code tariff.events} Kafka topic (Feature 21.4.3). Mirrors {@code tariff-price-changed.avsc}; only
 * {@code code} is used - campaign-service never mirrors tariff pricing data (ADR-027 Decision
 * Section 3/4). Unknown fields are ignored for forward-compatibility (ADR-019).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TariffPriceChangedPayload(
        String tariffId,
        String code
) {
}
