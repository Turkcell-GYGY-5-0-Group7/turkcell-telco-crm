package com.telco.catalog.application.query;

import com.telco.catalog.application.dto.AddonResponse;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.cqrs.Query;

/** Returns a paginated list of addons. When {@code tariffCode} is set, filters by that tariff. */
public record ListAddonsQuery(String tariffCode, int page, int size)
        implements Query<PageResult<AddonResponse>> {
}
