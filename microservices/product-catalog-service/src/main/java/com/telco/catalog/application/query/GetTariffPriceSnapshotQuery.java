package com.telco.catalog.application.query;

import com.telco.catalog.application.dto.PriceSnapshotResponse;
import com.telco.platform.cqrs.Query;

/** Returns the current price snapshot for a tariff code (internal endpoint for order-service). */
public record GetTariffPriceSnapshotQuery(String code) implements Query<PriceSnapshotResponse> {
}
