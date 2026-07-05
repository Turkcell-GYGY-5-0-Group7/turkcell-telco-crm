package com.telco.payment.infrastructure.psp;

import com.telco.payment.infrastructure.psp.MockPspAdapter.ForcedOutcome;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link MockPspAdapter}'s deterministic {@link ForcedOutcome} override
 * (feature 14.1.1). Verifies the override forces success/failure regardless of the random draw,
 * and that leaving it unset preserves the original random behaviour.
 */
class MockPspAdapterTest {

    private static final String PAYMENT_REQUEST_ID = "req-001";
    private static final BigDecimal AMOUNT = new BigDecimal("50.00");
    private static final String CUSTOMER_ID = "cust-001";

    @Test
    void forced_success_always_succeeds_even_when_random_would_fail() throws PspException {
        // Random seeded to always return 0.0, which is < FAILURE_RATE (0.10) and would normally fail.
        MockPspAdapter adapter = new MockPspAdapter(new AlwaysZeroRandom(), ForcedOutcome.SUCCESS);

        ChargeResult result = adapter.charge(PAYMENT_REQUEST_ID, AMOUNT, CUSTOMER_ID);

        assertThat(result.transactionId()).startsWith("mock-txn-");
    }

    @Test
    void forced_failure_always_throws_even_when_random_would_succeed() {
        // Random seeded to always return 0.99, which is >= FAILURE_RATE and would normally succeed.
        MockPspAdapter adapter = new MockPspAdapter(new AlwaysHighRandom(), ForcedOutcome.FAILURE);

        assertThatThrownBy(() -> adapter.charge(PAYMENT_REQUEST_ID, AMOUNT, CUSTOMER_ID))
                .isInstanceOf(PspException.class);
    }

    @Test
    void unset_override_preserves_default_random_behaviour() throws PspException {
        // No forced outcome: random draw >= FAILURE_RATE succeeds, matching pre-existing behaviour.
        MockPspAdapter adapter = new MockPspAdapter(new AlwaysHighRandom(), null);

        ChargeResult result = adapter.charge(PAYMENT_REQUEST_ID, AMOUNT, CUSTOMER_ID);

        assertThat(result.transactionId()).startsWith("mock-txn-");
    }

    @Test
    void unset_override_still_fails_on_unlucky_random_draw() {
        MockPspAdapter adapter = new MockPspAdapter(new AlwaysZeroRandom(), null);

        assertThatThrownBy(() -> adapter.charge(PAYMENT_REQUEST_ID, AMOUNT, CUSTOMER_ID))
                .isInstanceOf(PspException.class);
    }

    // --- Deterministic Random stand-ins ---

    private static final class AlwaysZeroRandom extends Random {
        @Override
        public double nextDouble() {
            return 0.0;
        }
    }

    private static final class AlwaysHighRandom extends Random {
        @Override
        public double nextDouble() {
            return 0.99;
        }
    }
}
