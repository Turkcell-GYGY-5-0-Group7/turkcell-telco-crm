package com.telco.webbff.dto;

import java.util.List;

/**
 * Catalog composition for {@code GET /bff/v1/onboarding/catalog}: tariffs and their addons shaped
 * for the onboarding wizard (web-bff contract). UI DTO, not {@code ApiResult<T>} (ADR-015 BFF
 * exception). Populated by the composition logic in 16.4.
 */
public record OnboardingCatalogResponse(
        List<TariffOption> tariffs,
        List<AddonOption> addons) {
}
