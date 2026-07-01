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
                    .uri("/api/v1/tariffs/{code}", tariffCode)
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
