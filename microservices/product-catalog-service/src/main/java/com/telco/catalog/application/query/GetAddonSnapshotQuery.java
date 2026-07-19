package com.telco.catalog.application.query;

import com.telco.catalog.application.dto.AddonSnapshotResponse;
import com.telco.platform.cqrs.Query;

/** Returns an internal pricing snapshot for an ACTIVE addon by code (order-service pricing). */
public record GetAddonSnapshotQuery(String code) implements Query<AddonSnapshotResponse> {
}
