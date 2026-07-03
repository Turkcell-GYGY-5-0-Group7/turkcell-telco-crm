package com.telco.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.telco.catalog.application.dto.TariffResponse;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Provider-side (consumer-driven) API contract gate for {@code GET /api/v1/tariffs/{id|code}}
 * (feature 14.1.2, NFR-16). product-catalog-service is the provider; the read model returned to
 * cross-service callers is {@link TariffResponse}.
 *
 * <p>The field set below is the union of what every downstream consumer binds from that response:
 * <ul>
 *   <li>order-service {@code TariffClientResponse}: id, code, name, monthlyFee, currency, version</li>
 *   <li>billing-service {@code TariffPricingResponse}: code, name, monthlyFee, currency</li>
 *   <li>usage-service {@code TariffAllowanceResponse}: minutesIncluded, smsIncluded, dataMbIncluded</li>
 * </ul>
 *
 * <p>Removing or renaming any of these fields is a breaking API change and fails this test, forcing a
 * conscious contract decision before consumers break at runtime. Adding fields is allowed (consumers
 * ignore unknown fields), so this is a subset assertion, not an exact match.
 */
class TariffApiContractTest {

    private static final Set<String> CONSUMER_REQUIRED_FIELDS = Set.of(
            "id", "code", "name", "monthlyFee", "currency", "version",
            "minutesIncluded", "smsIncluded", "dataMbIncluded");

    @Test
    void tariff_response_exposes_all_fields_consumed_across_services() {
        Set<String> fields = Arrays.stream(TariffResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());

        assertThat(fields)
                .as("TariffResponse must keep every field cross-service consumers "
                        + "(order/billing/usage) bind from GET /api/v1/tariffs; a removed/renamed "
                        + "field is a breaking API change (NFR-16)")
                .containsAll(CONSUMER_REQUIRED_FIELDS);
    }
}
