package com.telco.payment.resilience;

import com.telco.payment.infrastructure.psp.ChargeResult;
import com.telco.payment.infrastructure.psp.PspAdapter;
import com.telco.payment.infrastructure.psp.PspException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests for the PSP circuit breaker behaviour (Sprint 13.4.2).
 *
 * <p>No Spring context or Testcontainers — the circuit breaker is built programmatically
 * using the same {@link CircuitBreakerConfig} as {@code PspConfig} so the test is
 * authoritative about the production configuration values.
 */
class PspCircuitBreakerTest {

    private static final String PAYMENT_ID = "test-payment-001";
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");
    private static final String CUSTOMER_ID = "cust-001";

    /** Circuit breaker configuration matching {@code PspConfig.buildCircuitBreaker()}. */
    private static CircuitBreaker buildCircuitBreaker(String name) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .recordExceptions(PspException.class)
                .build();
        return CircuitBreakerRegistry.of(config).circuitBreaker(name);
    }

    /**
     * Wraps a {@link PspAdapter} delegate with a {@link CircuitBreaker}, using the same
     * programmatic decoration pattern as {@code PspConfig}.
     */
    private static PspAdapter wrap(PspAdapter delegate, CircuitBreaker cb) {
        return new PspAdapter() {
            @Override
            public ChargeResult charge(String paymentRequestId, BigDecimal amount, String customerId)
                    throws PspException {
                return cb.executeSupplier(() -> delegate.charge(paymentRequestId, amount, customerId));
            }

            @Override
            public ChargeResult refund(String paymentRequestId, BigDecimal amount, String customerId)
                    throws PspException {
                return cb.executeSupplier(() -> delegate.refund(paymentRequestId, amount, customerId));
            }
        };
    }

    @Test
    void circuit_opens_after_full_window_of_failures() {
        CircuitBreaker cb = buildCircuitBreaker("psp-open-test");
        PspAdapter wrapped = wrap(new AlwaysFailingPspAdapter(), cb);

        // Sliding window = 10, failure rate threshold = 50%.
        // Drive 10 calls all throwing PspException -> 100% failure rate -> circuit OPEN.
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> wrapped.charge(PAYMENT_ID, AMOUNT, CUSTOMER_ID))
                    .isInstanceOf(PspException.class);
        }

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // 11th call must be rejected immediately (CallNotPermittedException) without
        // touching the delegate.
        assertThatThrownBy(() -> wrapped.charge(PAYMENT_ID, AMOUNT, CUSTOMER_ID))
                .isInstanceOf(CallNotPermittedException.class);
    }

    @Test
    void circuit_stays_closed_when_failure_rate_is_below_threshold() {
        CircuitBreaker cb = buildCircuitBreaker("psp-closed-test");

        // 4 failures out of 10 calls = 40% failure rate < 50% threshold -> stays CLOSED.
        int[] callCount = {0};
        PspAdapter partiallyFailing = new PartiallyFailingPspAdapter(callCount, 4);
        PspAdapter wrapped = wrap(partiallyFailing, cb);

        for (int i = 0; i < 10; i++) {
            try {
                wrapped.charge(PAYMENT_ID, AMOUNT, CUSTOMER_ID);
            } catch (PspException ignored) {
                // expected on the first 4 calls
            }
        }

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void non_psp_exceptions_do_not_count_as_failures() {
        // Only PspException is recorded as failure (recordExceptions config).
        // RuntimeException should not count and must not move the circuit toward OPEN.
        CircuitBreaker cb = buildCircuitBreaker("psp-ignore-test");

        PspAdapter throwsRuntime = new PspAdapter() {
            @Override
            public ChargeResult charge(String id, BigDecimal amount, String customerId)
                    throws PspException {
                throw new RuntimeException("unexpected non-PSP error");
            }

            @Override
            public ChargeResult refund(String id, BigDecimal amount, String customerId)
                    throws PspException {
                throw new RuntimeException("unexpected non-PSP error");
            }
        };
        PspAdapter wrapped = wrap(throwsRuntime, cb);

        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> wrapped.charge(PAYMENT_ID, AMOUNT, CUSTOMER_ID))
                    .isInstanceOf(RuntimeException.class);
        }

        // RuntimeException is not in recordExceptions -> circuit remains CLOSED
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    // --- Test doubles ---

    private static final class AlwaysFailingPspAdapter implements PspAdapter {
        @Override
        public ChargeResult charge(String paymentRequestId, BigDecimal amount, String customerId)
                throws PspException {
            throw new PspException("simulated failure for circuit breaker test");
        }

        @Override
        public ChargeResult refund(String paymentRequestId, BigDecimal amount, String customerId)
                throws PspException {
            throw new PspException("simulated failure for circuit breaker test");
        }
    }

    private static final class PartiallyFailingPspAdapter implements PspAdapter {
        private final int[] callCount;
        private final int failCount;

        PartiallyFailingPspAdapter(int[] callCount, int failCount) {
            this.callCount = callCount;
            this.failCount = failCount;
        }

        @Override
        public ChargeResult charge(String paymentRequestId, BigDecimal amount, String customerId)
                throws PspException {
            if (callCount[0]++ < failCount) {
                throw new PspException("simulated failure #" + callCount[0]);
            }
            return new ChargeResult("mock-txn-" + callCount[0]);
        }

        @Override
        public ChargeResult refund(String paymentRequestId, BigDecimal amount, String customerId)
                throws PspException {
            return new ChargeResult("mock-refund-" + callCount[0]);
        }
    }
}
