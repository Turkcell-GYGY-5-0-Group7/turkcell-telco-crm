package com.telco.webbff.gateway;

import java.util.UUID;

/**
 * Partial view of customer-service's KYC document read model, deserialized from the gateway response
 * ({@code POST /api/v1/customers/{id}/documents}). {@code fileRef} is the object-storage reference the
 * upload returns. Local DTO; no cross-service coupling.
 */
public record GatewayDocument(
        UUID id,
        String fileRef) {
}
