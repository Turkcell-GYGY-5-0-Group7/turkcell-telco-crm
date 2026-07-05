package com.telco.catalog.application.query;

import com.telco.catalog.application.dto.TariffResponse;
import com.telco.platform.cqrs.Query;

import java.util.UUID;

/**
 * Fetches a single active tariff by its primary key. Internal lookup used by other services
 * (e.g. order-service) that hold the tariff's UUID rather than its human-readable code. Returns
 * 404 if not found or not active.
 */
public record GetTariffByIdQuery(UUID id) implements Query<TariffResponse> {
}
