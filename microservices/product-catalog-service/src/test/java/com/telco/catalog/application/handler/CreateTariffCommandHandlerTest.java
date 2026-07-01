package com.telco.catalog.application.handler;

import com.telco.catalog.application.command.CreateTariffCommand;
import com.telco.catalog.application.dto.TariffResponse;
import com.telco.catalog.domain.model.TariffType;
import com.telco.catalog.domain.service.TariffVersioningService;
import com.telco.catalog.infrastructure.persistence.TariffRepository;
import com.telco.platform.common.exception.BusinessRuleException;
import com.telco.platform.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateTariffCommandHandlerTest {

    @Mock private TariffRepository tariffRepository;
    @Mock private TariffVersioningService versioningService;
    @Mock private OutboxService outboxService;

    private CreateTariffCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CreateTariffCommandHandler(tariffRepository, versioningService, outboxService);
    }

    private CreateTariffCommand validCommand() {
        return new CreateTariffCommand(
                "PLAN-X", "Plan X", TariffType.POSTPAID,
                new BigDecimal("49.99"), "TRY", 100, 50, 1024, null,
                Instant.parse("2026-01-01T00:00:00Z"), null);
    }

    @Test
    void creates_tariff_saves_snapshot_and_publishes_event() {
        when(tariffRepository.existsByCode("PLAN-X")).thenReturn(false);
        when(tariffRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TariffResponse response = handler.handle(validCommand());

        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo("PLAN-X");
        verify(tariffRepository).save(any());
        verify(versioningService).createInitialSnapshot(any());
        verify(outboxService).publish(eq("tariff"), anyString(), eq("tariff.created.v1"), any());
    }

    @Test
    void rejects_duplicate_tariff_code() {
        when(tariffRepository.existsByCode("PLAN-X")).thenReturn(true);

        assertThatThrownBy(() -> handler.handle(validCommand()))
                .isInstanceOf(BusinessRuleException.class);
        verify(outboxService, never()).publish(any(), any(), any(), any());
    }
}
