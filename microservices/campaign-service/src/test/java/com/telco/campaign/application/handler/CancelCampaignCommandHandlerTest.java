package com.telco.campaign.application.handler;

import com.telco.campaign.application.command.CancelCampaignCommand;
import com.telco.campaign.application.dto.CampaignResponse;
import com.telco.campaign.domain.model.Campaign;
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
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelCampaignCommandHandlerTest {

    @Mock private CampaignRepository campaignRepository;
    @Mock private OutboxService outboxService;

    private CancelCampaignCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CancelCampaignCommandHandler(campaignRepository, outboxService);
    }

    private static Campaign draftCampaign() {
        return Campaign.create("WELCOME10", "Welcome", null,
                DiscountType.PERCENTAGE, new BigDecimal("10.00"), Set.of("TARIFF-A"),
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-12-31T00:00:00Z"),
                null, 1);
    }

    @Test
    void cancels_draft_campaign() {
        Campaign campaign = draftCampaign();
        when(campaignRepository.findById(campaign.getId())).thenReturn(Optional.of(campaign));
        when(campaignRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CampaignResponse response = handler.handle(new CancelCampaignCommand(campaign.getId()));

        assertThat(response.status()).isEqualTo("CANCELLED");
        verify(outboxService).publish(eq("campaign"), eq(campaign.getId().toString()),
                eq("campaign.cancelled.v1"), any());
    }

    @Test
    void rejects_cancelling_an_already_cancelled_campaign() {
        Campaign campaign = draftCampaign();
        campaign.cancel();
        when(campaignRepository.findById(campaign.getId())).thenReturn(Optional.of(campaign));

        assertThatThrownBy(() -> handler.handle(new CancelCampaignCommand(campaign.getId())))
                .isInstanceOf(BusinessRuleException.class);
        verify(outboxService, never()).publish(any(), any(), any(), any());
    }
}
