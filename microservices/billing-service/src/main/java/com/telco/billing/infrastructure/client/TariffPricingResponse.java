package com.telco.billing.infrastructure.client;

import java.math.BigDecimal;

public record TariffPricingResponse(
        String code,
        String name,
        BigDecimal monthlyFee,
        String currency
) {}
