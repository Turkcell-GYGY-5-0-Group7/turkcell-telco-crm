package com.telco.billing.infrastructure.client;

import com.telco.platform.common.api.ApiResult;
import com.telco.platform.common.exception.DependencyFailureException;
import com.telco.platform.common.exception.ResourceNotFoundException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class ProductCatalogBillingClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductCatalogBillingClient.class);
    private static final ParameterizedTypeReference<ApiResult<TariffPricingResponse>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    public ProductCatalogBillingClient(RestClient productCatalogRestClient) {
        this.restClient = productCatalogRestClient;
    }

    /**
     * Fetches tariff pricing via the tokenless, permitAll internal price-snapshot endpoint
     * ({@code GET /internal/tariffs/{code}/price-snapshot}); this client sends no JWT, so it must
     * never call an authenticated route (tech-lead ruling 2026-07-06, tariff endpoint
     * internal-surface fix).
     */
    @CircuitBreaker(name = "product-catalog-service", fallbackMethod = "getTariffPricingFallback")
    public TariffPricingResponse getTariffPricing(String tariffCode) {
        try {
            ApiResult<TariffPricingResponse> result = restClient.get()
                    .uri("/internal/tariffs/{code}/price-snapshot", tariffCode)
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
            LOGGER.error("Failed to fetch tariff pricing for tariffCode={}", tariffCode, e);
            throw new DependencyFailureException(
                    "Failed to fetch tariff from product-catalog-service: " + tariffCode, e);
        }
    }

    // --- Fallback method ---

    private TariffPricingResponse getTariffPricingFallback(String tariffCode, Throwable t) {
        // A 404 from the downstream service must propagate as ResourceNotFoundException, not
        // be masked as a dependency failure. Re-throw transparently so callers see the correct
        // error type and the circuit does not swallow logical errors.
        if (t instanceof ResourceNotFoundException) {
            throw (ResourceNotFoundException) t;
        }
        LOGGER.warn("product-catalog-service circuit breaker open for tariffCode={}: {}",
                tariffCode, t.getMessage());
        throw new DependencyFailureException(
                "product-catalog-service unavailable for tariffCode: " + tariffCode, t);
    }
}
