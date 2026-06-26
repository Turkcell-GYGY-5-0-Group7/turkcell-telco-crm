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
 */
public class MockPspAdapter implements PspAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(MockPspAdapter.class);
    private static final double FAILURE_RATE = 0.10;

    private final Random random;

    public MockPspAdapter() {
        this.random = new Random();
    }

    /** Constructor for tests that inject a seeded {@link Random} for deterministic behaviour. */
    MockPspAdapter(Random random) {
        this.random = random;
    }

    @Override
    public ChargeResult charge(String paymentRequestId, BigDecimal amount, String customerId)
            throws PspException {
        LOGGER.debug("MockPSP charge: requestId={} amount={} customerId={}",
                paymentRequestId, amount, customerId);

        if (random.nextDouble() < FAILURE_RATE) {
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
