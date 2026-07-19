package com.telco.catalog.application.query;

import com.telco.catalog.application.dto.TariffResponse;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.cqrs.Query;

/**
 * Returns a paginated list of currently active tariffs within their effective window.
 *
 * @param sort optional {@code field,asc|desc} sort expression; null/blank means {@code createdAt,desc}
 */
public record ListTariffsQuery(int page, int size, String sort)
        implements Query<PageResult<TariffResponse>> {
}
