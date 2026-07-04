package com.telco.payment.infrastructure.psp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Random;
import java.util.UUID;

/**
 * Mock PSP adapter for the MVP (FR-25). Simulates a real PSP with configurable success rate:
 * 90% of charge calls succeed; 10% throw {@link PspException} to represent a technical failure.
 *
 * <p>This bean is NOT registered directly. {@code PspConfig} wraps it with a Resilience4j
 * circuit breaker and registers the wrapped version as the {@link PspAdapter} bean.
 *
 * <p>The random outcome can be overridden with a deterministic {@link ForcedOutcome} (wired from
 * {@code telco.psp.mock.force-outcome} by {@code PspConfig}) so CI/acceptance runs can force
 * every charge to succeed or fail without depending on chance. Leaving the property unset
 * preserves the original ~10% random failure rate for normal dev/prod-like runs.
 */
public class MockPspAdapter implements PspAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(MockPspAdapter.class);
    private static final double FAILURE_RATE = 0.10;

    /** Deterministic override for {@link #charge}. {@code null} preserves the random behaviour. */
    public enum ForcedOutcome {
        SUCCESS,
        FAILURE
    }

    private final Random random;
    private final ForcedOutcome forcedOutcome;

    public MockPspAdapter() {
        this(new Random(), null);
    }

    public MockPspAdapter(ForcedOutcome forcedOutcome) {
        this(new Random(), forcedOutcome);
    }

    /** Constructor for tests that inject a seeded {@link Random} for deterministic behaviour. */
    MockPspAdapter(Random random) {
        this(random, null);
    }

    MockPspAdapter(Random random, ForcedOutcome forcedOutcome) {
        this.random = random;
        this.forcedOutcome = forcedOutcome;
    }

    @Override
    public ChargeResult charge(String paymentRequestId, BigDecimal amount, String customerId)
            throws PspException {
        LOGGER.debug("MockPSP charge: requestId={} amount={} customerId={}",
                paymentRequestId, amount, customerId);

        if (forcedOutcome == ForcedOutcome.FAILURE) {
            LOGGER.warn("MockPSP forced technical failure (telco.psp.mock.force-outcome=FAILURE) "
                    + "for requestId={}", paymentRequestId);
            throw new PspException("MockPSP: forced technical failure for requestId=" + paymentRequestId);
        }

        if (forcedOutcome != ForcedOutcome.SUCCESS && random.nextDouble() < FAILURE_RATE) {
            LOGGER.warn("MockPSP simulated technical failure for requestId={}", paymentRequestId);
            throw new PspException("MockPSP: simulated technical failure for requestId=" + paymentRequestId);
        }

        String transactionId = "mock-txn-" + UUID.randomUUID();
        LOGGER.debug("MockPSP charge succeeded: transactionId={}", transactionId);
        return new ChargeResult(transactionId);
    }

    @Override
    public ChargeResult refund(String paymentRequestId, BigDecimal amount, String customerId)
            throws PspException {
        LOGGER.debug("MockPSP refund: requestId={} amount={} customerId={}",
                paymentRequestId, amount, customerId);
        // Mock refunds always succeed in the MVP.
        String transactionId = "mock-refund-" + UUID.randomUUID();
        LOGGER.debug("MockPSP refund succeeded: transactionId={}", transactionId);
        return new ChargeResult(transactionId);
    }
}
