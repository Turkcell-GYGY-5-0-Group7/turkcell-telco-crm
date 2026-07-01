package com.telco.catalog.application.handler;

import com.telco.catalog.application.dto.TariffResponse;
import com.telco.catalog.application.query.GetTariffQuery;
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
class GetTariffQueryHandlerTest {

    @Mock private TariffRepository tariffRepository;

    private GetTariffQueryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetTariffQueryHandler(tariffRepository);
    }

    @Test
    void returns_response_for_active_tariff() {
        Tariff tariff = Tariff.create("PLAN-A", "Plan A", TariffType.POSTPAID,
                new BigDecimal("49.99"), "TRY", 100, 50, 1024, null,
                Instant.parse("2026-01-01T00:00:00Z"), null);
        tariff.activate();
        when(tariffRepository.findByCode("PLAN-A")).thenReturn(Optional.of(tariff));

        TariffResponse response = handler.handle(new GetTariffQuery("PLAN-A"));

        assertThat(response.code()).isEqualTo("PLAN-A");
    }

    @Test
    void throws_not_found_when_code_does_not_exist() {
        when(tariffRepository.findByCode("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new GetTariffQuery("MISSING")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void throws_not_found_when_tariff_is_not_active() {
        Tariff tariff = Tariff.create("PLAN-D", "Draft", TariffType.POSTPAID,
                BigDecimal.ONE, "TRY", 0, 0, 0, null,
                Instant.parse("2026-01-01T00:00:00Z"), null);
        // status remains DRAFT — not activated
        when(tariffRepository.findByCode("PLAN-D")).thenReturn(Optional.of(tariff));

        assertThatThrownBy(() -> handler.handle(new GetTariffQuery("PLAN-D")))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
