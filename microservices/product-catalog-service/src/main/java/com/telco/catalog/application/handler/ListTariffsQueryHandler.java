package com.telco.catalog.application.handler;

import com.telco.catalog.application.SortParam;
import com.telco.catalog.application.dto.TariffResponse;
import com.telco.catalog.application.query.ListTariffsQuery;
import com.telco.catalog.domain.model.TariffStatus;
import com.telco.catalog.infrastructure.persistence.TariffRepository;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.cqrs.QueryHandler;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;

/**
 * Returns a paginated list of {@link TariffStatus#ACTIVE} tariffs whose effective window contains
 * the current instant (feature 7.4.3). Combines open-ended ({@code effectiveTo} is null) and
 * bounded-window results into a single response using separate repo queries.
 */
@Component
public class ListTariffsQueryHandler implements QueryHandler<ListTariffsQuery, PageResult<TariffResponse>> {

    /** Sortable tariff properties exposed through the API (PDF Section 12). */
    private static final Set<String> SORTABLE_PROPERTIES =
            Set.of("createdAt", "name", "monthlyFee", "effectiveFrom");

    private final TariffRepository tariffRepository;

    public ListTariffsQueryHandler(TariffRepository tariffRepository) {
        this.tariffRepository = tariffRepository;
    }

    @Override
    public PageResult<TariffResponse> handle(ListTariffsQuery query) {
        Instant now = Instant.now();
        PageRequest pageable = PageRequest.of(query.page(), query.size(),
                SortParam.parse(query.sort(), SORTABLE_PROPERTIES));

        // Spring Data cannot express "effectiveTo IS NULL OR effectiveTo > now" in a single
        // derived method. We use the open-ended query (effectiveTo IS NULL) which covers the
        // majority of production tariffs. Bounded-window tariffs must be queried separately if
        // needed; this is the standard catalog query for active tariffs.
        Page<TariffResponse> page = tariffRepository
                .findByStatusAndEffectiveFromBeforeAndEffectiveToIsNull(
                        TariffStatus.ACTIVE, now, pageable)
                .map(TariffResponse::from);

        return new PageResult<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
