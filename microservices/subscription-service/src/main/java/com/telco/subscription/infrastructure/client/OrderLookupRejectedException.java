package com.telco.subscription.infrastructure.client;

/**
 * TERMINAL signal raised when order-service rejects the activation lookup with a non-404 4xx
 * (401/403/400/409/422 - any {@link org.springframework.web.client.HttpClientErrorException} other
 * than 404). A 4xx is a contract or authorization defect on the request itself: it cannot heal by
 * Kafka redelivery, so it must fail closed to saga compensation rather than dead-loop the listener.
 *
 * <p>Distinct from {@link com.telco.platform.common.exception.ResourceNotFoundException} (404, mapped
 * to reason {@code ORDER_NOT_FOUND}) and from
 * {@link com.telco.platform.common.exception.DependencyFailureException} (5xx / IO / timeout /
 * circuit-open, TRANSIENT -> retry). The consumer maps this exception to
 * {@code FailSubscriptionActivationCommand} with reason {@code ORDER_LOOKUP_REJECTED}.
 */
public class OrderLookupRejectedException extends RuntimeException {

    public OrderLookupRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
