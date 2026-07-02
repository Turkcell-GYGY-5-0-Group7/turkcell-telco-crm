package com.telco.order.resilience;

import com.telco.order.config.ResilienceConfig;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests for the order-service circuit breakers (Sprint 13.4.2).
 *
 * <p>No Spring context — {@link ResilienceConfig} is instantiated directly because it carries
 * no Spring infrastructure dependencies (it only builds Resilience4j objects).
 *
 * <p>Note on test isolation: {@code ResilienceConfig} uses a static {@link
 * io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry}. Each test therefore uses a
 * distinct circuit breaker NAME to ensure independent state:
 * <ul>
 *   <li>{@code circuit_opens_after_threshold_failures} — uses "customer-service"</li>
 *   <li>{@code product_catalog_circuit_stays_closed_on_successful_calls} — uses
 *       "product-catalog-service" and only makes successful calls, so it does not interfere
 *       with the open state created in the first test.</li>
 * </ul>
 */
class CustomerClientCircuitBreakerTest {

    @Test
    void circuit_opens_after_threshold_failures() {
        ResilienceConfig resilienceConfig = new ResilienceConfig();
        // "customer-service" — default config: all exceptions count, sliding window = 10,
        // failure rate threshold = 50%.
        CircuitBreaker circuitBreaker = resilienceConfig.customerServiceCircuitBreaker();

        // Drive 10 failures (100% failure rate > 50% threshold).
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> circuitBreaker.executeRunnable(() -> {
                throw new RuntimeException("simulated customer-service downstream failure");
            })).isInstanceOf(RuntimeException.class);
        }

        // Sliding window is full and failure rate exceeds the threshold.
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // The next call must be rejected immediately without reaching any downstream logic.
        assertThatThrownBy(() -> circuitBreaker.executeRunnable(() -> {}))
                .isInstanceOf(CallNotPermittedException.class);
    }

    @Test
    void product_catalog_circuit_stays_closed_on_successful_calls() {
        ResilienceConfig resilienceConfig = new ResilienceConfig();
        // Uses "product-catalog-service" — a different circuit breaker name from "customer-service"
        // above, so this test is not affected by the OPEN state induced in the previous test.
        CircuitBreaker circuitBreaker = resilienceConfig.productCatalogCircuitBreaker();

        // 10 successful calls -> 0% failure rate -> circuit must remain CLOSED.
        for (int i = 0; i < 10; i++) {
            circuitBreaker.executeRunnable(() -> {
                // no-op: simulates a successful downstream call
            });
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
