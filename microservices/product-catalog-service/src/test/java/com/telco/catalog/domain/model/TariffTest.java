package com.telco.catalog.domain.model;

import com.telco.platform.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TariffTest {

    private static Tariff draftTariff() {
        return Tariff.create("PLAN-A", "Plan A", TariffType.POSTPAID,
                new BigDecimal("49.99"), "TRY", 100, 50, 1024, null,
                Instant.parse("2026-01-01T00:00:00Z"), null);
    }

    @Test
    void create_initialises_draft_status_and_version_one() {
        Tariff tariff = draftTariff();

        assertThat(tariff.getId()).isNotNull();
        assertThat(tariff.getCode()).isEqualTo("PLAN-A");
        assertThat(tariff.getStatus()).isEqualTo(TariffStatus.DRAFT);
        assertThat(tariff.getVersion()).isEqualTo(1);
        assertThat(tariff.getMonthlyFee()).isEqualByComparingTo("49.99");
        assertThat(tariff.getAddons()).isEmpty();
    }

    @Test
    void create_rejects_effectiveTo_before_effectiveFrom() {
        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-05-01T00:00:00Z");

        assertThatThrownBy(() -> Tariff.create("X", "X", TariffType.PREPAID,
                BigDecimal.ONE, "TRY", 0, 0, 0, null, from, to))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void create_accepts_null_effectiveTo_as_open_ended() {
        Tariff tariff = Tariff.create("OPEN", "Open", TariffType.POSTPAID,
                BigDecimal.ONE, "TRY", 0, 0, 0, null,
                Instant.now(), null);

        assertThat(tariff.getEffectiveTo()).isNull();
    }

    @Test
    void applyPriceChange_updates_fee_and_bumps_version() {
        Tariff tariff = draftTariff();
        int versionBefore = tariff.getVersion();

        tariff.applyPriceChange(new BigDecimal("59.99"));

        assertThat(tariff.getMonthlyFee()).isEqualByComparingTo("59.99");
        assertThat(tariff.getVersion()).isEqualTo(versionBefore + 1);
    }

    @Test
    void applyPriceChange_rejects_negative_fee() {
        Tariff tariff = draftTariff();

        assertThatThrownBy(() -> tariff.applyPriceChange(new BigDecimal("-1.00")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void applyPriceChange_accepts_zero_fee() {
        Tariff tariff = draftTariff();

        tariff.applyPriceChange(BigDecimal.ZERO);

        assertThat(tariff.getMonthlyFee()).isEqualByComparingTo("0.00");
    }

    @Test
    void activate_transitions_to_active_status() {
        Tariff tariff = draftTariff();

        tariff.activate();

        assertThat(tariff.getStatus()).isEqualTo(TariffStatus.ACTIVE);
    }

    @Test
    void retire_transitions_to_retired_status() {
        Tariff tariff = draftTariff();
        tariff.activate();

        tariff.retire();

        assertThat(tariff.getStatus()).isEqualTo(TariffStatus.RETIRED);
    }
}
