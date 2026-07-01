package com.telco.usage.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QuotaTest {

    private static final UUID SUB_ID = UUID.randomUUID();
    private static final UUID CUST_ID = UUID.randomUUID();
    private static final Instant START = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-07-01T00:00:00Z");

    // --- factory ---

    @Test
    void create_sets_remaining_equal_to_totals() {
        Quota q = Quota.create(SUB_ID, CUST_ID, START, END, 300, 200, 1024);

        assertThat(q.getMinutesRemaining()).isEqualTo(300);
        assertThat(q.getSmsRemaining()).isEqualTo(200);
        assertThat(q.getMbRemaining()).isEqualTo(1024);
    }

    @Test
    void create_initialises_notification_flags_to_false() {
        Quota q = Quota.create(SUB_ID, CUST_ID, START, END, 300, 200, 1024);

        assertThat(q.isThresholdNotified()).isFalse();
        assertThat(q.isExceededNotified()).isFalse();
    }

    // --- VOICE decrement happy path ---

    @Test
    void voice_decrement_reduces_minutes_remaining() {
        Quota q = Quota.create(SUB_ID, CUST_ID, START, END, 1000, 200, 2048);

        Quota.DecrementResult result = q.decrement(UsageType.VOICE, 10);

        assertThat(q.getMinutesRemaining()).isEqualTo(990);
        assertThat(result.overage()).isFalse();
        assertThat(result.overageQuantity()).isZero();
        assertThat(result.thresholdCrossed()).isFalse();
        assertThat(result.exceededCrossed()).isFalse();
    }

    // --- SMS decrement happy path ---

    @Test
    void sms_decrement_reduces_sms_remaining() {
        Quota q = Quota.create(SUB_ID, CUST_ID, START, END, 1000, 500, 2048);

        Quota.DecrementResult result = q.decrement(UsageType.SMS, 50);

        assertThat(q.getSmsRemaining()).isEqualTo(450);
        assertThat(result.overage()).isFalse();
        assertThat(result.overageQuantity()).isZero();
    }

    // --- DATA decrement happy path ---

    @Test
    void data_decrement_reduces_mb_remaining() {
        Quota q = Quota.create(SUB_ID, CUST_ID, START, END, 1000, 500, 2048);

        Quota.DecrementResult result = q.decrement(UsageType.DATA, 100);

        assertThat(q.getMbRemaining()).isEqualTo(1948);
        assertThat(result.overage()).isFalse();
        assertThat(result.overageQuantity()).isZero();
    }

    // --- overage ---

    @Test
    void decrement_more_than_remaining_caps_at_zero_and_flags_overage() {
        Quota q = Quota.create(SUB_ID, CUST_ID, START, END, 10, 200, 2048);

        Quota.DecrementResult result = q.decrement(UsageType.VOICE, 25);

        assertThat(q.getMinutesRemaining()).isZero();
        assertThat(result.overage()).isTrue();
        assertThat(result.overageQuantity()).isEqualTo(15);
    }

    // --- threshold crossing ---

    @Test
    void threshold_crossed_when_remaining_drops_to_exactly_20_percent() {
        // 81 consumed from 100 → remaining 19 ≤ 100/5=20 → threshold
        Quota q = Quota.create(SUB_ID, CUST_ID, START, END, 100, 200, 2048);

        Quota.DecrementResult result = q.decrement(UsageType.VOICE, 81);

        assertThat(result.thresholdCrossed()).isTrue();
        assertThat(result.exceededCrossed()).isFalse();
        assertThat(q.isThresholdNotified()).isTrue();
        assertThat(q.getMinutesRemaining()).isEqualTo(19);
    }

    @Test
    void threshold_not_crossed_when_remaining_stays_above_20_percent() {
        // 79 consumed from 100 → remaining 21 > 20 → no threshold
        Quota q = Quota.create(SUB_ID, CUST_ID, START, END, 100, 200, 2048);

        Quota.DecrementResult result = q.decrement(UsageType.VOICE, 79);

        assertThat(result.thresholdCrossed()).isFalse();
        assertThat(q.isThresholdNotified()).isFalse();
    }

    @Test
    void threshold_crossing_is_idempotent_second_call_returns_false() {
        Quota q = Quota.create(SUB_ID, CUST_ID, START, END, 100, 200, 2048);
        q.decrement(UsageType.VOICE, 81); // first decrement crosses threshold

        Quota.DecrementResult second = q.decrement(UsageType.VOICE, 1);

        assertThat(second.thresholdCrossed()).isFalse();
        assertThat(q.isThresholdNotified()).isTrue();
    }

    // --- exceeded crossing ---

    @Test
    void exceeded_crossed_when_remaining_reaches_zero() {
        // total=100, pre-decrement to trigger threshold first, then decrement to 0
        Quota q = Quota.create(SUB_ID, CUST_ID, START, END, 100, 200, 2048);
        q.decrement(UsageType.VOICE, 81); // threshold triggered here, remaining=19

        Quota.DecrementResult result = q.decrement(UsageType.VOICE, 19); // remaining→0

        assertThat(result.exceededCrossed()).isTrue();
        assertThat(result.thresholdCrossed()).isFalse(); // already notified
        assertThat(q.isExceededNotified()).isTrue();
        assertThat(q.getMinutesRemaining()).isZero();
    }

    @Test
    void exceeded_crossing_is_idempotent_second_call_returns_false() {
        Quota q = Quota.create(SUB_ID, CUST_ID, START, END, 10, 200, 2048);
        q.decrement(UsageType.VOICE, 10); // → 0, both threshold and exceeded triggered

        Quota.DecrementResult second = q.decrement(UsageType.VOICE, 5); // overage

        assertThat(second.exceededCrossed()).isFalse();
        assertThat(q.isExceededNotified()).isTrue();
    }

    // --- both threshold and exceeded in one decrement ---

    @Test
    void both_threshold_and_exceeded_crossed_when_depleted_from_full_small_quota() {
        // total=20, remaining=20 — single decrement of 20 → remaining=0
        // threshold: 0 <= 20/5=4 AND !false → crossed
        // exceeded: 0==0 AND !false → crossed
        Quota q = Quota.create(SUB_ID, CUST_ID, START, END, 20, 200, 2048);

        Quota.DecrementResult result = q.decrement(UsageType.VOICE, 20);

        assertThat(result.thresholdCrossed()).isTrue();
        assertThat(result.exceededCrossed()).isTrue();
        assertThat(q.isThresholdNotified()).isTrue();
        assertThat(q.isExceededNotified()).isTrue();
        assertThat(q.getMinutesRemaining()).isZero();
    }

    // --- other buckets are not affected ---

    @Test
    void voice_decrement_does_not_affect_sms_or_data_remaining() {
        Quota q = Quota.create(SUB_ID, CUST_ID, START, END, 100, 50, 200);

        q.decrement(UsageType.VOICE, 90);

        assertThat(q.getSmsRemaining()).isEqualTo(50);
        assertThat(q.getMbRemaining()).isEqualTo(200);
    }

    // --- updatedAt is set ---

    @Test
    void decrement_sets_updated_at() {
        Quota q = Quota.create(SUB_ID, CUST_ID, START, END, 100, 50, 200);
        assertThat(q.getUpdatedAt()).isNull();

        q.decrement(UsageType.VOICE, 1);

        assertThat(q.getUpdatedAt()).isNotNull();
    }
}
