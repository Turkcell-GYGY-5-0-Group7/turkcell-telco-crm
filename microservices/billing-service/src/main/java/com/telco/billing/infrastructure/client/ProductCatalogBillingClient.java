package com.telco.billing.infrastructure.client;

import com.telco.platform.common.api.ApiResult;
import com.telco.platform.common.exception.DependencyFailureException;
import com.telco.platform.common.exception.ResourceNotFoundException;
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

    public TariffPricingResponse getTariffPricing(String tariffCode) {
        try {
            ApiResult<TariffPricingResponse> result = restClient.get()
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
            LOGGER.error("Failed to fetch tariff pricing for tariffCode={}", tariffCode, e);
            throw new DependencyFailureException(
                    "Failed to fetch tariff from product-catalog-service: " + tariffCode, e);
        }
    }
}
