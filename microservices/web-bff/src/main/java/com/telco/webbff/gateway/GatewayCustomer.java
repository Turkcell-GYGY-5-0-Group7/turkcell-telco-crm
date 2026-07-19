package com.telco.webbff.gateway;

import java.util.UUID;

/**
 * Partial view of customer-service's customer read model, deserialized from the gateway response
 * ({@code POST /api/v1/customers} on registration, {@code GET /api/v1/customers/{id}} on profile
 * reads). web-bff needs the generated id (to place the order and to scope the KYC document upload),
 * the status, and the name fields used to shape the home/account profile summary. The identity number
 * is intentionally not consumed here - it is returned masked by customer-service and the BFF has no
 * need for it. Unknown properties are ignored. Local DTO; no cross-service coupling (ADR-022).
 */
public record GatewayCustomer(
        UUID id,
        String firstName,
        String lastName,
        String status) {
}
