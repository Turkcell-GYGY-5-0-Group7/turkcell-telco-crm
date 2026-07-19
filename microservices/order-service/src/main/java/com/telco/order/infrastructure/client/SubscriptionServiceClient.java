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
 * HTTP client for subscription-service. Uses Spring's {@link RestClient} with a Resilience4j
 * {@link CircuitBreaker} for fault tolerance, mirroring {@link ProductCatalogServiceClient}'s
 * structure (Sprint 24 Feature 24.2).
 *
 * <p>Targets {@code GET /internal/subscriptions/{id}}, a tokenless internal lookup by primary key
 * (no JWT): {@code SubscriptionSecurityConfig} permits {@code /internal/**} explicitly, and the
 * gateway excludes {@code /internal/**} from public traffic (network-perimeter trust, the same
 * tokenless internal-call pattern {@link ProductCatalogServiceClient} established for
 * {@code /internal/tariffs/**}) - this client sends no token.
 *
 * <p>Fail-closed by design: ADDON and PLAN_CHANGE orders must not be created against a
 * subscription that cannot be verified as ACTIVE and owned by the ordering customer, so a
 * subscription-service outage blocks those order kinds (unlike the fail-open
 * {@link CampaignServiceClient}, whose data is optional pricing input).
 */
@Component
public class SubscriptionServiceClient {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionServiceClient.class);
    private static final ParameterizedTypeReference<ApiResult<SubscriptionClientResponse>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public SubscriptionServiceClient(RestClient subscriptionRestClient,
                                     CircuitBreaker subscriptionServiceCircuitBreaker) {
        this.restClient = subscriptionRestClient;
        this.circuitBreaker = subscriptionServiceCircuitBreaker;
    }

    /**
     * Validates a subscription exists and returns its ownership/status/tariff snapshot. Throws
     * {@link ResourceNotFoundException} (404) if the subscription is not found, or
     * {@link DependencyFailureException} (502/503) on circuit-open or unexpected failure.
     */
    public SubscriptionClientResponse getSubscription(UUID subscriptionId) {
        try {
            return CircuitBreaker.decorateCallable(circuitBreaker, () -> {
                ApiResult<SubscriptionClientResponse> result = restClient.get()
                        .uri("/internal/subscriptions/{subscriptionId}", subscriptionId)
                        .retrieve()
                        .body(RESPONSE_TYPE);
                if (result == null || !result.success() || result.data() == null) {
                    throw new DependencyFailureException(
                            "Unexpected response from subscription-service for subscriptionId: "
                                    + subscriptionId, null);
                }
                return result.data();
            }).call();
        } catch (CallNotPermittedException e) {
            log.warn("subscription-service circuit breaker OPEN for subscriptionId={}", subscriptionId);
            throw new DependencyFailureException("subscription-service is currently unavailable", e);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException("Subscription not found: " + subscriptionId);
        } catch (DependencyFailureException | ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to call subscription-service for subscriptionId={}", subscriptionId, e);
            throw new DependencyFailureException("Failed to validate subscription: " + subscriptionId, e);
        }
    }
}
