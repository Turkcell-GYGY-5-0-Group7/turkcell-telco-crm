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
 * HTTP client for customer-service. Uses Spring's {@link RestClient} with a Resilience4j
 * {@link CircuitBreaker} for fault tolerance (starter-resilience not yet available; flagged
 * for migration to the platform starter when it ships).
 *
 * <p>Targets {@code GET /internal/customers/{customerId}}, a tokenless internal existence/status
 * lookup (no JWT): {@code CustomerSecurityConfig} permits it explicitly, mirroring the
 * already-public {@code /internal/orders/{orderId}} route's "internal endpoint, no PII, no
 * authentication required" contract - the internal shape carries no PII and this client sends no
 * token.
 */
@Component
public class CustomerServiceClient {

    private static final Logger log = LoggerFactory.getLogger(CustomerServiceClient.class);
    private static final ParameterizedTypeReference<ApiResult<CustomerClientResponse>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public CustomerServiceClient(RestClient customerRestClient,
                                 CircuitBreaker customerServiceCircuitBreaker) {
        this.restClient = customerRestClient;
        this.circuitBreaker = customerServiceCircuitBreaker;
    }

    /**
     * Validates that the customer exists. Throws {@link ResourceNotFoundException} (404) if the
     * customer is not found, or {@link DependencyFailureException} (502/503) on circuit-open or
     * unexpected failure.
     */
    public CustomerClientResponse getCustomer(UUID customerId) {
        try {
            return CircuitBreaker.decorateCallable(circuitBreaker, () -> {
                ApiResult<CustomerClientResponse> result = restClient.get()
                        .uri("/internal/customers/{customerId}", customerId)
                        .retrieve()
                        .body(RESPONSE_TYPE);
                if (result == null || !result.success()) {
                    throw new DependencyFailureException(
                            "Unexpected response from customer-service for customerId: " + customerId, null);
                }
                return result.data();
            }).call();
        } catch (CallNotPermittedException e) {
            log.warn("customer-service circuit breaker OPEN for customerId={}", customerId);
            throw new DependencyFailureException("customer-service is currently unavailable", e);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException("Customer not found: " + customerId);
        } catch (DependencyFailureException | ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to call customer-service for customerId={}", customerId, e);
            throw new DependencyFailureException("Failed to validate customer: " + customerId, e);
        }
    }
}
