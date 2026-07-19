package com.telco.catalog.application.handler;

import com.telco.catalog.application.dto.AddonSnapshotResponse;
import com.telco.catalog.application.query.GetAddonSnapshotQuery;
import com.telco.catalog.domain.model.Addon;
import com.telco.catalog.infrastructure.persistence.AddonRepository;
import com.telco.platform.common.exception.CommonErrorCode;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.QueryHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Returns a lightweight addon snapshot for the given code (feature 24.1). Used by order-service
 * for addon price resolution at order creation time. Only serves {@code ACTIVE} addons; all
 * others return 404, mirroring {@link GetTariffPriceSnapshotQueryHandler}.
 */
@Component
public class GetAddonSnapshotQueryHandler
        implements QueryHandler<GetAddonSnapshotQuery, AddonSnapshotResponse> {

    private final AddonRepository addonRepository;

    public GetAddonSnapshotQueryHandler(AddonRepository addonRepository) {
        this.addonRepository = addonRepository;
    }

    @Override
    public AddonSnapshotResponse handle(GetAddonSnapshotQuery query) {
        Addon addon = addonRepository.findByCode(query.code())
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "Addon not found with code: " + query.code(),
                        Map.of("code", query.code())));

        if (!"ACTIVE".equals(addon.getStatus())) {
            throw new ResourceNotFoundException(
                    CommonErrorCode.RESOURCE_NOT_FOUND,
                    "Addon is not active: " + query.code(),
                    Map.of("code", query.code(), "status", addon.getStatus()));
        }

        return AddonSnapshotResponse.from(addon);
    }
}
