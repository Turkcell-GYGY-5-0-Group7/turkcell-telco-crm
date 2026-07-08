package com.telco.usage.infrastructure.client;

import com.telco.platform.common.api.ApiResult;
import com.telco.platform.common.exception.DependencyFailureException;
import com.telco.platform.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * HTTP client for the product-catalog-service tariff read API.
 * Called during quota provisioning to obtain a tariff's usage allowances.
 *
 * <p>Uses the tokenless {@code GET /internal/tariffs/{code}/allowance-snapshot} endpoint (bug
 * found via live acceptance testing, 2026-07-06): this client is invoked from a Kafka consumer
 * ({@code SubscriptionActivatedEventConsumer}), which holds no caller JWT to forward, so it must
 * never call an authenticated route - it was previously calling the authenticated
 * {@code GET /api/v1/tariffs/{code}} and getting 401s on every single quota provisioning attempt.
 * Mirrors the same tech-lead-ruled pattern (2026-07-06, tariff endpoint internal-surface fix) as
 * billing-service's {@code ProductCatalogBillingClient}/price-snapshot; the route now lives under
 * {@code /internal/**}, which the gateway excludes from public traffic.
 */
@Component
public class ProductCatalogClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductCatalogClient.class);
    private static final ParameterizedTypeReference<ApiResult<TariffAllowanceResponse>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    public ProductCatalogClient(RestClient productCatalogRestClient) {
        this.restClient = productCatalogRestClient;
    }

    /**
     * Fetches usage allowances for a tariff by code.
     *
     * @throws ResourceNotFoundException if the tariff does not exist in the catalog
     * @throws DependencyFailureException on any other connectivity or protocol failure
     */
    public TariffAllowanceResponse getTariffAllowances(String tariffCode) {
        try {
            ApiResult<TariffAllowanceResponse> result = restClient.get()
                    .uri("/internal/tariffs/{code}/allowance-snapshot", tariffCode)
                    .retrieve()
                    .body(RESPONSE_TYPE);

            if (result == null || !result.success() || result.data() == null) {
                throw new DependencyFailureException(
                        "Unexpected response from product-catalog-service for tariffCode: " + tariffCode, null);
            }
            return result.data();
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException("Tariff not found in catalog: " + tariffCode);
        } catch (DependencyFailureException | ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Failed to fetch tariff allowances for tariffCode={}", tariffCode, e);
            throw new DependencyFailureException(
                    "Failed to fetch tariff from product-catalog-service: " + tariffCode, e);
        }
    }
}
