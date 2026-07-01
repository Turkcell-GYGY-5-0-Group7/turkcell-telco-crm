package com.telco.subscription.infrastructure.client;

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
 * HTTP client for order-service. Uses Spring's {@link RestClient} with a Resilience4j
 * {@link CircuitBreaker} for fault tolerance, mirroring the order-service downstream-client pattern.
 *
 * <p>This is the single synchronous hop the {@code payment.completed.v1} activation path makes
 * (architecture Option (b)): the order carries the authoritative {@code customerId} and the tariff
 * snapshot ({@code tariffCode} + {@code tariffVersion}) the subscription needs to activate, so the
 * subscription never reaches into product-catalog. It targets the tokenless internal endpoint
 * {@code GET /internal/orders/{orderId}} (no JWT, no ownership guard) - a service-to-service lookup,
 * so no {@code Authorization} header is sent (mirrors order-service's tokenless downstream clients).
 *
 * <p>Failure-mode mapping is deliberate so the consumer can distinguish transient from terminal:
 * <ul>
 *   <li>404 -&gt; {@link ResourceNotFoundException} (TERMINAL: the order is genuinely missing
 *       post-payment, a data-integrity error -&gt; compensate, reason {@code ORDER_NOT_FOUND}).</li>
 *   <li>any other 4xx (401/403/400/409/422) -&gt; {@link OrderLookupRejectedException} (TERMINAL: a
 *       contract/auth defect that cannot heal by redelivery -&gt; fail closed to compensation, reason
 *       {@code ORDER_LOOKUP_REJECTED}). It must NOT dead-loop the listener.</li>
 *   <li>circuit-open / IO / 5xx / unexpected -&gt; {@link DependencyFailureException} (TRANSIENT:
 *       order-service is down or degraded -&gt; let the Kafka listener retry; the outage must heal by
 *       redelivery, never by compensation).</li>
 * </ul>
 */
@Component
public class OrderServiceClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderServiceClient.class);
    private static final ParameterizedTypeReference<ApiResult<OrderClientResponse>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public OrderServiceClient(RestClient orderRestClient,
                              CircuitBreaker orderServiceCircuitBreaker) {
        this.restClient = orderRestClient;
        this.circuitBreaker = orderServiceCircuitBreaker;
    }

    /**
     * Fetches an order by id from the tokenless internal endpoint. Throws
     * {@link ResourceNotFoundException} (404, terminal -&gt; {@code ORDER_NOT_FOUND}),
     * {@link OrderLookupRejectedException} (any other 4xx, terminal -&gt; {@code ORDER_LOOKUP_REJECTED}),
     * or {@link DependencyFailureException} (transient) on circuit-open, 5xx, IO or an otherwise
     * unexpected failure.
     */
    public OrderClientResponse getOrder(UUID orderId) {
        try {
            return CircuitBreaker.decorateCallable(circuitBreaker, () -> {
                ApiResult<OrderClientResponse> result = restClient.get()
                        .uri("/internal/orders/{orderId}", orderId)
                        .retrieve()
                        .body(RESPONSE_TYPE);
                if (result == null || !result.success() || result.data() == null) {
                    throw new DependencyFailureException(
                            "Unexpected response from order-service for orderId: " + orderId, null);
                }
                return result.data();
            }).call();
        } catch (CallNotPermittedException e) {
            LOGGER.warn("order-service circuit breaker OPEN for orderId={}", orderId);
            throw new DependencyFailureException("order-service is currently unavailable", e);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        } catch (HttpClientErrorException e) {
            // Any other 4xx (401/403/400/409/422) is a contract/auth defect: it cannot heal by
            // redelivery, so fail closed to compensation rather than dead-loop the listener.
            LOGGER.warn("order-service rejected lookup for orderId={} with status={}; failing closed",
                    orderId, e.getStatusCode());
            throw new OrderLookupRejectedException(
                    "order-service rejected order lookup for orderId " + orderId
                            + " with status " + e.getStatusCode(), e);
        } catch (DependencyFailureException | ResourceNotFoundException
                 | OrderLookupRejectedException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Failed to call order-service for orderId={}", orderId, e);
            throw new DependencyFailureException("Failed to fetch order: " + orderId, e);
        }
    }
}
