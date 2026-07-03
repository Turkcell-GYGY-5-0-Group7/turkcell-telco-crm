package com.telco.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.telco.customer.application.dto.CustomerResponse;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Provider-side (consumer-driven) API contract gate for {@code GET /api/v1/customers/{id}}
 * (feature 14.1.2, NFR-16). customer-service is the provider; order-service is the cross-service
 * consumer during order validation.
 *
 * <p>order-service's {@code CustomerClientResponse} binds only {@code id} and {@code status}.
 * Removing or renaming either is a breaking API change and fails this test. Adding fields is allowed
 * (the consumer ignores unknown fields), so this is a subset assertion.
 */
class CustomerApiContractTest {

    private static final Set<String> CONSUMER_REQUIRED_FIELDS = Set.of("id", "status");

    @Test
    void customer_response_exposes_fields_order_service_consumes() {
        Set<String> fields = Arrays.stream(CustomerResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());

        assertThat(fields)
                .as("CustomerResponse must keep the fields order-service binds from "
                        + "GET /api/v1/customers/{id}; a removed/renamed field is a breaking API "
                        + "change (NFR-16)")
                .containsAll(CONSUMER_REQUIRED_FIELDS);
    }
}
