package com.telco.webbff.dto;

import java.time.LocalDate;

/**
 * Registration details forwarded to customer-service ({@code POST /api/v1/customers}) when the
 * onboarding wizard registers a new customer. Shape mirrors the domain register contract
 * (type + name + identity number + date of birth); customer-service performs the TCKN/VKN validation
 * and PII encryption - web-bff only forwards. The identity number is never logged by the BFF (it is
 * relayed straight through to the gateway, not read into a log line).
 */
public record CustomerRegistration(
        String type,
        String firstName,
        String lastName,
        String identityNumber,
        LocalDate dateOfBirth) {
}
