package com.telco.order.infrastructure.client;

import com.telco.platform.common.api.ApiResult;
import com.telco.platform.common.exception.DependencyFailureException;
import com.telco.platform.common.exception.ResourceNotFoundException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * HTTP client for product-catalog-service. Uses Spring's {@link RestClient} with a Resilience4j
 * {@link CircuitBreaker} for fault tolerance.
 *
 * <p>Targets {@code GET /internal/tariffs/{tariffId}}, a tokenless internal lookup by primary
 * key (no JWT): {@code CatalogSecurityConfig} permits {@code /internal/**} explicitly, and the
 * gateway excludes {@code /internal/**} from public traffic (network-perimeter trust, tech-lead
 * ruling 2026-07-06, tariff endpoint internal-surface fix) - tariff data carries no PII and this
 * client sends no token.
 */
@Component
public class ProductCatalogServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ProductCatalogServiceClient.class);
    private static final ParameterizedTypeReference<ApiResult<TariffClientResponse>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResult<AddonSnapshotClientResponse>> ADDON_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public ProductCatalogServiceClient(RestClient productCatalogRestClient,
                                       CircuitBreaker productCatalogCircuitBreaker) {
        this.restClient = productCatalogRestClient;
        this.circuitBreaker = productCatalogCircuitBreaker;
    }

    /**
     * Validates a tariff exists and returns its price details. Throws
     * {@link ResourceNotFoundException} (404) if the tariff is not found, or
     * {@link DependencyFailureException} (502/503) on circuit-open or unexpected failure.
     */
    public TariffClientResponse getTariff(UUID tariffId) {
        try {
            return CircuitBreaker.decorateCallable(circuitBreaker, () -> {
                ApiResult<TariffClientResponse> result = restClient.get()
                        .uri("/internal/tariffs/{tariffId}", tariffId)
                        .retrieve()
                        .body(RESPONSE_TYPE);
                if (result == null || !result.success()) {
                    throw new DependencyFailureException(
                            "Unexpected response from product-catalog-service for tariffId: " + tariffId, null);
                }
                return result.data();
            }).call();
        } catch (CallNotPermittedException e) {
            log.warn("product-catalog-service circuit breaker OPEN for tariffId={}", tariffId);
            throw new DependencyFailureException("product-catalog-service is currently unavailable", e);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException("Tariff not found: " + tariffId);
        } catch (DependencyFailureException | ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to call product-catalog-service for tariffId={}", tariffId, e);
            throw new DependencyFailureException("Failed to validate tariff: " + tariffId, e);
        }
    }

    /**
     * Fetches the addon pricing/allowance snapshot for an ADDON order item (Sprint 24 Features
     * 24.1/24.2) from the tokenless {@code GET /internal/addons/{code}/snapshot} endpoint. Throws
     * {@link ResourceNotFoundException} (404) if the addon does not exist or is not ACTIVE, or
     * {@link DependencyFailureException} (502/503) on circuit-open or unexpected failure -
     * fail-closed like {@link #getTariff}: an addon is mandatory pricing data.
     */
    public AddonSnapshotClientResponse getAddonSnapshot(String code) {
        try {
            return CircuitBreaker.decorateCallable(circuitBreaker, () -> {
                ApiResult<AddonSnapshotClientResponse> result = restClient.get()
                        .uri("/internal/addons/{code}/snapshot", code)
                        .retrieve()
                        .body(ADDON_RESPONSE_TYPE);
                if (result == null || !result.success() || result.data() == null) {
                    throw new DependencyFailureException(
                            "Unexpected response from product-catalog-service for addon code: " + code, null);
                }
                return result.data();
            }).call();
        } catch (CallNotPermittedException e) {
            log.warn("product-catalog-service circuit breaker OPEN for addon code={}", code);
            throw new DependencyFailureException("product-catalog-service is currently unavailable", e);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException("Addon not found: " + code);
        } catch (DependencyFailureException | ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to call product-catalog-service for addon code={}", code, e);
            throw new DependencyFailureException("Failed to validate addon: " + code, e);
        }
    }
}
