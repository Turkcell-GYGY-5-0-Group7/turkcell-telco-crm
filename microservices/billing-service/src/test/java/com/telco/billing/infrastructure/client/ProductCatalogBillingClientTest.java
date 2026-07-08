package com.telco.billing.infrastructure.client;

import com.telco.platform.common.exception.DependencyFailureException;
import com.telco.platform.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Covers {@link ProductCatalogBillingClient}'s success/404/dependency-failure paths and its
 * Resilience4j fallback method (only reachable in production once the "product-catalog-service"
 * circuit trips open) — 14.3.2 saga-path coverage for billing's one outbound HTTP dependency.
 */
class ProductCatalogBillingClientTest {

    @Test
    void returns_tariff_pricing_on_a_successful_response() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://product-catalog-service");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://product-catalog-service/internal/tariffs/POSTPAID-M/price-snapshot"))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"code":"POSTPAID-M","name":"Postpaid M","monthlyFee":149.99,"currency":"TRY"},"error":null,"meta":null}
                        """, MediaType.APPLICATION_JSON));

        ProductCatalogBillingClient client = new ProductCatalogBillingClient(builder.build());
        TariffPricingResponse response = client.getTariffPricing("POSTPAID-M");

        assertThat(response.code()).isEqualTo("POSTPAID-M");
        assertThat(response.monthlyFee()).isEqualByComparingTo("149.99");
        server.verify();
    }

    @Test
    void throws_resource_not_found_when_catalog_returns_404() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://product-catalog-service");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://product-catalog-service/internal/tariffs/UNKNOWN/price-snapshot"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        ProductCatalogBillingClient client = new ProductCatalogBillingClient(builder.build());

        assertThatThrownBy(() -> client.getTariffPricing("UNKNOWN"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void throws_dependency_failure_on_an_unexpected_response_shape() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://product-catalog-service");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://product-catalog-service/internal/tariffs/POSTPAID-M/price-snapshot"))
                .andRespond(withSuccess("""
                        {"success":false,"data":null,"error":null,"meta":null}
                        """, MediaType.APPLICATION_JSON));

        ProductCatalogBillingClient client = new ProductCatalogBillingClient(builder.build());

        assertThatThrownBy(() -> client.getTariffPricing("POSTPAID-M"))
                .isInstanceOf(DependencyFailureException.class);
    }

    @Test
    void fallback_rethrows_resource_not_found_transparently_when_circuit_opens_after_a_404() throws Exception {
        ProductCatalogBillingClient client = new ProductCatalogBillingClient(RestClient.builder().build());
        Method fallback = ProductCatalogBillingClient.class.getDeclaredMethod(
                "getTariffPricingFallback", String.class, Throwable.class);
        fallback.setAccessible(true);

        assertThatThrownBy(() -> invoke(fallback, client, "POSTPAID-M",
                new ResourceNotFoundException("Tariff not found in catalog: POSTPAID-M")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void fallback_wraps_other_failures_as_dependency_failure_when_circuit_is_open() throws Exception {
        ProductCatalogBillingClient client = new ProductCatalogBillingClient(RestClient.builder().build());
        Method fallback = ProductCatalogBillingClient.class.getDeclaredMethod(
                "getTariffPricingFallback", String.class, Throwable.class);
        fallback.setAccessible(true);

        assertThatThrownBy(() -> invoke(fallback, client, "POSTPAID-M", new RuntimeException("circuit open")))
                .isInstanceOf(DependencyFailureException.class);
    }

    private static void invoke(Method method, Object target, Object... args) throws Throwable {
        try {
            method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
