package com.telco.catalog.application.query;

import com.telco.catalog.application.dto.TariffResponse;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.cqrs.Query;

/** Returns a paginated list of currently active tariffs within their effective window. */
public record ListTariffsQuery(int page, int size) implements Query<PageResult<TariffResponse>> {
}
