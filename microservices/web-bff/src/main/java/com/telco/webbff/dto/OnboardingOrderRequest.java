package com.telco.webbff.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Request body for {@code POST /bff/v1/onboarding/order}. Drives the onboarding composition
 * (web-bff contract, ADR-022): the BFF either reuses an existing customer ({@code customerId}
 * present) or registers a new one from {@code customer} and uploads its KYC document, then resolves
 * {@code tariffCode} to a tariff id and places the order through the gateway carrying the mandatory
 * {@code Idempotency-Key}.
 *
 * <p>Exactly one identity path is expected: supply {@code customerId} to reuse, or {@code customer}
 * (plus {@code kycDocument}) to register. The controller bean-validates only {@code tariffCode} here;
 * the register-vs-reuse rule is enforced in the composition service so a missing block yields a 400
 * rather than a 500.
 */
public record OnboardingOrderRequest(
        String customerId,
        @Valid CustomerRegistration customer,
        @Valid KycDocument kycDocument,
        @NotBlank String tariffCode,
        List<String> addonCodes) {
}
