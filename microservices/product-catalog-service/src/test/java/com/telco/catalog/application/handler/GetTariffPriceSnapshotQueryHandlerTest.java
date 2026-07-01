package com.telco.catalog.application.handler;

import com.telco.catalog.application.dto.PriceSnapshotResponse;
import com.telco.catalog.application.query.GetTariffPriceSnapshotQuery;
import com.telco.catalog.domain.model.Tariff;
import com.telco.catalog.domain.model.TariffType;
import com.telco.catalog.infrastructure.persistence.TariffRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetTariffPriceSnapshotQueryHandlerTest {

    @Mock private TariffRepository tariffRepository;

    private GetTariffPriceSnapshotQueryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetTariffPriceSnapshotQueryHandler(tariffRepository);
    }

    @Test
    void returns_snapshot_for_active_tariff() {
        Tariff tariff = Tariff.create("PLAN-A", "Plan A", TariffType.POSTPAID,
                new BigDecimal("49.99"), "TRY", 100, 50, 1024, null,
                Instant.parse("2026-01-01T00:00:00Z"), null);
        tariff.activate();
        when(tariffRepository.findByCode("PLAN-A")).thenReturn(Optional.of(tariff));

        PriceSnapshotResponse snapshot = handler.handle(new GetTariffPriceSnapshotQuery("PLAN-A"));

        assertThat(snapshot.code()).isEqualTo("PLAN-A");
        assertThat(snapshot.monthlyFee()).isEqualByComparingTo("49.99");
        assertThat(snapshot.currency()).isEqualTo("TRY");
    }

    @Test
    void throws_not_found_when_code_missing() {
        when(tariffRepository.findByCode("NONE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new GetTariffPriceSnapshotQuery("NONE")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void throws_not_found_when_tariff_is_draft_not_active() {
        Tariff tariff = Tariff.create("PLAN-D", "Draft", TariffType.POSTPAID,
                BigDecimal.ONE, "TRY", 0, 0, 0, null,
                Instant.parse("2026-01-01T00:00:00Z"), null);
        when(tariffRepository.findByCode("PLAN-D")).thenReturn(Optional.of(tariff));

        assertThatThrownBy(() -> handler.handle(new GetTariffPriceSnapshotQuery("PLAN-D")))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
