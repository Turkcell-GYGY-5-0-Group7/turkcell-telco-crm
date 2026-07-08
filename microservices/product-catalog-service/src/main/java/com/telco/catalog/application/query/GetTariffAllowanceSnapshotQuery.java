package com.telco.catalog.application.query;

import com.telco.catalog.application.dto.AllowanceSnapshotResponse;
import com.telco.platform.cqrs.Query;

/** Returns the current usage-allowance snapshot for a tariff code (internal endpoint for usage-service). */
public record GetTariffAllowanceSnapshotQuery(String code) implements Query<AllowanceSnapshotResponse> {
}
