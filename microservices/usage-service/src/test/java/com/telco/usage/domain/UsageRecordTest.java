package com.telco.usage.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UsageRecordTest {

    @Test
    void create_assigns_unique_id_and_captures_all_fields() {
        UUID subId = UUID.randomUUID();
        UUID quotaId = UUID.randomUUID();

        UsageRecord record = UsageRecord.create(subId, quotaId, UsageType.VOICE, 120, false, "CDR-001");

        assertThat(record.getId()).isNotNull();
        assertThat(record.getSubscriptionId()).isEqualTo(subId);
        assertThat(record.getQuotaId()).isEqualTo(quotaId);
        assertThat(record.getType()).isEqualTo(UsageType.VOICE);
        assertThat(record.getQuantity()).isEqualTo(120);
        assertThat(record.isOverage()).isFalse();
        assertThat(record.getCdrRef()).isEqualTo("CDR-001");
        assertThat(record.getRecordedAt()).isNotNull();
    }

    @Test
    void create_sets_overage_flag_when_true() {
        UsageRecord record = UsageRecord.create(
                UUID.randomUUID(), UUID.randomUUID(), UsageType.DATA, 50, true, "CDR-002");

        assertThat(record.isOverage()).isTrue();
    }

    @Test
    void create_allows_null_quota_id_for_unmatched_cdrs() {
        UsageRecord record = UsageRecord.create(
                UUID.randomUUID(), null, UsageType.SMS, 5, false, "CDR-003");

        assertThat(record.getQuotaId()).isNull();
    }

    @Test
    void create_rejects_null_cdr_ref() {
        assertThatThrownBy(() -> UsageRecord.create(
                UUID.randomUUID(), UUID.randomUUID(), UsageType.VOICE, 10, false, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void two_records_with_same_params_get_different_ids() {
        UUID subId = UUID.randomUUID();

        UsageRecord r1 = UsageRecord.create(subId, null, UsageType.SMS, 1, false, "CDR-A");
        UsageRecord r2 = UsageRecord.create(subId, null, UsageType.SMS, 1, false, "CDR-B");

        assertThat(r1.getId()).isNotEqualTo(r2.getId());
    }
}
