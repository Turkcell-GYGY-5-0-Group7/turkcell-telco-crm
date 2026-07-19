package com.telco.campaign.application.handler;

import com.telco.campaign.application.command.LogStaleTariffReferenceCommand;
import com.telco.campaign.domain.model.Campaign;
import com.telco.campaign.domain.model.CampaignStatus;
import com.telco.campaign.domain.model.DiscountType;
import com.telco.campaign.infrastructure.persistence.CampaignRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogStaleTariffReferenceCommandHandlerTest {

    @Mock private CampaignRepository campaignRepository;

    private LogStaleTariffReferenceCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new LogStaleTariffReferenceCommandHandler(campaignRepository);
    }

    private static Campaign activeCampaign() {
        Campaign campaign = Campaign.create("WELCOME10", "Welcome", null,
                DiscountType.PERCENTAGE, new BigDecimal("10.00"), Set.of("TARIFF-A"),
                Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2999-01-01T00:00:00Z"),
                null, 1);
        campaign.activate();
        return campaign;
    }

    @Test
    void logs_but_never_mutates_matched_campaigns() {
        Campaign campaign = activeCampaign();
        when(campaignRepository.findByStatusAndApplicableTariffCode(CampaignStatus.ACTIVE, "TARIFF-A"))
                .thenReturn(List.of(campaign));

        handler.handle(new LogStaleTariffReferenceCommand("TARIFF-A", "msg-1"));

        // Read-only: no flag is ever set by this consumer path.
        assertThat(campaign.isStaleTariffFlag()).isFalse();
    }

    @Test
    void no_active_campaign_referencing_the_code_is_a_no_op() {
        when(campaignRepository.findByStatusAndApplicableTariffCode(CampaignStatus.ACTIVE, "TARIFF-Z"))
                .thenReturn(List.of());

        handler.handle(new LogStaleTariffReferenceCommand("TARIFF-Z", "msg-2"));
        // No exception, no side effects - the assertion is simply that this completes.
    }
}
