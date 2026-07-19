package com.telco.catalog.application.query;

import com.telco.catalog.application.dto.AddonResponse;
import com.telco.platform.cqrs.Query;

/** Fetches a single addon by its unique code (FR-09: order-service prices addon orders from it). */
public record GetAddonByCodeQuery(String code) implements Query<AddonResponse> {
}
