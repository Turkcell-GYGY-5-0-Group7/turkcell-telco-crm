package com.telco.catalog.application.query;

import com.telco.catalog.application.dto.TariffResponse;
import com.telco.platform.cqrs.Query;

/** Fetches a single active tariff by its code. Returns 404 if not found or not active. */
public record GetTariffQuery(String code) implements Query<TariffResponse> {
}
