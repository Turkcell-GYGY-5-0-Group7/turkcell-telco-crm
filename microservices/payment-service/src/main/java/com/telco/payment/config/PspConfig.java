package com.telco.payment.config;

import com.telco.payment.infrastructure.psp.ChargeResult;
import com.telco.payment.infrastructure.psp.MockPspAdapter;
import com.telco.payment.infrastructure.psp.MockPspAdapter.ForcedOutcome;
import com.telco.payment.infrastructure.psp.PspAdapter;
import com.telco.payment.infrastructure.psp.PspException;
import com.telco.platform.common.exception.DependencyFailureException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Wires the PSP adapter bean with a Resilience4j circuit breaker configured in code
 * (no starter-resilience available yet; flagged for migration - see platform-capabilities.md).
 *
 * <p>Circuit breaker parameters:
 * <ul>
 *   <li>Sliding window: last 10 calls (COUNT_BASED)</li>
 *   <li>Opens when failure rate >= 50%</li>
 *   <li>Wait in OPEN state: 30 seconds</li>
 *   <li>Permitted calls in HALF_OPEN: 3</li>
 *   <li>Recorded exception: {@link PspException}</li>
 * </ul>
 *
 * <p>When the circuit is OPEN, {@link CallNotPermittedException} is caught and re-thrown as
 * {@link DependencyFailureException} (HTTP 503 via platform GlobalExceptionHandler).
 *
 * <p>{@link PspException} extends {@link RuntimeException}, so {@code executeSupplier} is used
 * without requiring a checked-exception supplier API.
 */
@Configuration
public class PspConfig {

    private static final String PSP_CIRCUIT_BREAKER_NAME = "psp";

    /**
     * Deterministic override for {@link MockPspAdapter#charge}, read from
     * {@code telco.psp.mock.force-outcome} ({@code SUCCESS}/{@code FAILURE}). Unset (default)
     * preserves the mock's original ~10% random failure rate; CI/acceptance runs can set this
     * env-backed property for reproducible outcomes.
     */
    @Value("${telco.psp.mock.force-outcome:}")
    private String forceOutcome;

    @Bean
    public PspAdapter pspAdapter() {
        MockPspAdapter mock = new MockPspAdapter(resolveForcedOutcome());
        CircuitBreaker circuitBreaker = buildCircuitBreaker();

        return new PspAdapter() {

            @Override
            public ChargeResult charge(String paymentRequestId,
                                       BigDecimal amount,
                                       String customerId) throws PspException {
                try {
                    return circuitBreaker.executeSupplier(
                            () -> mock.charge(paymentRequestId, amount, customerId));
                } catch (PspException e) {
                    // Re-throw domain failures so the handler records the failed attempt.
                    throw e;
                } catch (CallNotPermittedException e) {
                    throw new DependencyFailureException(
                            "PSP circuit breaker is OPEN - payment will be retried by scheduler", e);
                }
            }

            @Override
            public ChargeResult refund(String paymentRequestId,
                                       BigDecimal amount,
                                       String customerId) throws PspException {
                try {
                    return circuitBreaker.executeSupplier(
                            () -> mock.refund(paymentRequestId, amount, customerId));
                } catch (PspException e) {
                    throw e;
                } catch (CallNotPermittedException e) {
                    throw new DependencyFailureException(
                            "PSP circuit breaker is OPEN - refund unavailable", e);
                }
            }
        };
    }

    private CircuitBreaker buildCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .recordExceptions(PspException.class)
                .build();
        return CircuitBreakerRegistry.of(config).circuitBreaker(PSP_CIRCUIT_BREAKER_NAME);
    }

    /**
     * Parses {@code telco.psp.mock.force-outcome} into a {@link ForcedOutcome}, or {@code null}
     * when unset/blank so {@link MockPspAdapter} falls back to its random behaviour. An
     * unrecognized value is treated as unset rather than failing startup.
     */
    private ForcedOutcome resolveForcedOutcome() {
        if (forceOutcome == null || forceOutcome.isBlank()) {
            return null;
        }
        try {
            return ForcedOutcome.valueOf(forceOutcome.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
