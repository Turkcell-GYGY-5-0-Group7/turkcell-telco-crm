package com.telco.campaign.application.handler;

import com.telco.campaign.application.command.CreateCampaignCommand;
import com.telco.campaign.application.dto.CampaignResponse;
import com.telco.campaign.domain.model.DiscountType;
import com.telco.campaign.infrastructure.persistence.CampaignRepository;
import com.telco.platform.common.exception.BusinessRuleException;
import com.telco.platform.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateCampaignCommandHandlerTest {

    @Mock private CampaignRepository campaignRepository;
    @Mock private OutboxService outboxService;

    private CreateCampaignCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CreateCampaignCommandHandler(campaignRepository, outboxService);
    }

    private static CreateCampaignCommand validCommand() {
        return new CreateCampaignCommand(
                "WELCOME10", "Welcome 10%", "10% off",
                DiscountType.PERCENTAGE, new BigDecimal("10.00"),
                Set.of("TARIFF-A"),
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-12-31T00:00:00Z"),
                1000, 1);
    }

    @Test
    void creates_campaign_in_draft_status() {
        when(campaignRepository.existsByCode("WELCOME10")).thenReturn(false);
        when(campaignRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CampaignResponse response = handler.handle(validCommand());

        assertThat(response.code()).isEqualTo("WELCOME10");
        assertThat(response.status()).isEqualTo("DRAFT");
        verify(outboxService).publish(eq("campaign"), eq(response.id().toString()),
                eq("campaign.created.v1"), any());
    }

    @Test
    void rejects_duplicate_campaign_code() {
        when(campaignRepository.existsByCode("WELCOME10")).thenReturn(true);

        assertThatThrownBy(() -> handler.handle(validCommand()))
                .isInstanceOf(BusinessRuleException.class);
        verify(outboxService, never()).publish(any(), any(), any(), any());
    }
}
