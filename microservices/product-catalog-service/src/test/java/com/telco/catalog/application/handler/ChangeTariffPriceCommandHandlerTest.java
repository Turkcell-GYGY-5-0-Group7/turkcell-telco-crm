package com.telco.catalog.application.handler;

import com.telco.catalog.application.command.ChangeTariffPriceCommand;
import com.telco.catalog.application.dto.TariffResponse;
import com.telco.catalog.domain.model.Tariff;
import com.telco.catalog.domain.model.TariffType;
import com.telco.catalog.domain.service.TariffVersioningService;
import com.telco.catalog.infrastructure.persistence.TariffRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.outbox.OutboxService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangeTariffPriceCommandHandlerTest {

    @Mock private TariffRepository tariffRepository;
    @Mock private TariffVersioningService versioningService;
    @Mock private OutboxService outboxService;

    private ChangeTariffPriceCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ChangeTariffPriceCommandHandler(tariffRepository, versioningService, outboxService);
    }

    @Test
    void changes_price_saves_and_publishes_event() {
        Tariff tariff = Tariff.create("PLAN-A", "Plan A", TariffType.POSTPAID,
                new BigDecimal("49.99"), "TRY", 100, 50, 1024, null,
                Instant.parse("2026-01-01T00:00:00Z"), null);
        when(tariffRepository.findByCode("PLAN-A")).thenReturn(Optional.of(tariff));
        when(tariffRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TariffResponse response = handler.handle(new ChangeTariffPriceCommand("PLAN-A", new BigDecimal("59.99")));

        assertThat(response).isNotNull();
        verify(versioningService).applyPriceChange(eq(tariff), eq(new BigDecimal("59.99")));
        verify(tariffRepository).save(tariff);
        verify(outboxService).publish(eq("tariff"), anyString(), eq("tariff.price-changed.v1"), any());
    }

    @Test
    void throws_not_found_when_tariff_code_does_not_exist() {
        when(tariffRepository.findByCode("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new ChangeTariffPriceCommand("MISSING", BigDecimal.ONE)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(outboxService, never()).publish(any(), any(), any(), any());
    }
}
