package com.telco.usage.application.handler;

import com.telco.usage.application.command.ProvisionQuotaCommand;
import com.telco.usage.domain.Quota;
import com.telco.usage.infrastructure.client.ProductCatalogClient;
import com.telco.usage.infrastructure.client.TariffAllowanceResponse;
import com.telco.usage.infrastructure.persistence.QuotaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProvisionQuotaCommandHandlerTest {

    @Mock private QuotaRepository quotaRepository;
    @Mock private ProductCatalogClient productCatalogClient;

    private ProvisionQuotaCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ProvisionQuotaCommandHandler(quotaRepository, productCatalogClient);
    }

    @Test
    void provisions_quota_with_tariff_allowances() {
        UUID subscriptionId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant activatedAt = Instant.parse("2026-06-15T10:00:00Z");
        when(productCatalogClient.getTariffAllowances("POSTPAID-S"))
                .thenReturn(new TariffAllowanceResponse(200, 100, 2048));
        when(quotaRepository.existsBySubscriptionIdAndPeriodStart(any(), any())).thenReturn(false);
        when(quotaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler.handle(new ProvisionQuotaCommand(subscriptionId, customerId, "POSTPAID-S", activatedAt));

        ArgumentCaptor<Quota> captor = ArgumentCaptor.forClass(Quota.class);
        verify(quotaRepository).save(captor.capture());
        Quota saved = captor.getValue();
        assertThat(saved.getSubscriptionId()).isEqualTo(subscriptionId);
        assertThat(saved.getCustomerId()).isEqualTo(customerId);
        assertThat(saved.getMinutesTotal()).isEqualTo(200L);
        assertThat(saved.getSmsTotal()).isEqualTo(100L);
        assertThat(saved.getMbTotal()).isEqualTo(2048L);
        assertThat(saved.getMinutesRemaining()).isEqualTo(200L);
    }

    @Test
    void billing_period_is_calendar_month_of_activation() {
        when(productCatalogClient.getTariffAllowances(any())).thenReturn(new TariffAllowanceResponse(100, 50, 1024));
        when(quotaRepository.existsBySubscriptionIdAndPeriodStart(any(), any())).thenReturn(false);
        when(quotaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler.handle(new ProvisionQuotaCommand(
                UUID.randomUUID(), UUID.randomUUID(), "TARIFF-X",
                Instant.parse("2026-06-15T10:00:00Z")));

        ArgumentCaptor<Quota> captor = ArgumentCaptor.forClass(Quota.class);
        verify(quotaRepository).save(captor.capture());
        assertThat(captor.getValue().getPeriodStart()).isEqualTo(Instant.parse("2026-06-01T00:00:00Z"));
        assertThat(captor.getValue().getPeriodEnd()).isEqualTo(Instant.parse("2026-07-01T00:00:00Z"));
    }

    @Test
    void duplicate_activation_is_ignored_gracefully() {
        when(productCatalogClient.getTariffAllowances(any())).thenReturn(new TariffAllowanceResponse(100, 50, 1024));
        when(quotaRepository.existsBySubscriptionIdAndPeriodStart(any(), any())).thenReturn(true);

        // Must not propagate and must not save — quota already provisioned.
        handler.handle(new ProvisionQuotaCommand(UUID.randomUUID(), UUID.randomUUID(), "TARIFF-X", Instant.now()));

        verify(quotaRepository, never()).save(any());
    }

    @Test
    void data_only_tariff_creates_zero_voice_and_sms_quota() {
        when(productCatalogClient.getTariffAllowances("DATA-ONLY"))
                .thenReturn(new TariffAllowanceResponse(0, 0, 5120));
        when(quotaRepository.existsBySubscriptionIdAndPeriodStart(any(), any())).thenReturn(false);
        when(quotaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler.handle(new ProvisionQuotaCommand(UUID.randomUUID(), UUID.randomUUID(), "DATA-ONLY", Instant.now()));

        ArgumentCaptor<Quota> captor = ArgumentCaptor.forClass(Quota.class);
        verify(quotaRepository).save(captor.capture());
        assertThat(captor.getValue().getMinutesTotal()).isZero();
        assertThat(captor.getValue().getSmsTotal()).isZero();
        assertThat(captor.getValue().getMbTotal()).isEqualTo(5120L);
    }
}
