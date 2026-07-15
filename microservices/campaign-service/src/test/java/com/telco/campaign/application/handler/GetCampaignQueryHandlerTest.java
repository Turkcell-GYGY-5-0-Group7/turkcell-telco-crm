package com.telco.campaign.application.handler;

import com.telco.campaign.application.dto.CampaignResponse;
import com.telco.campaign.application.query.GetCampaignQuery;
import com.telco.campaign.domain.model.Campaign;
import com.telco.campaign.domain.model.DiscountType;
import com.telco.campaign.infrastructure.persistence.CampaignRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetCampaignQueryHandlerTest {

    @Mock private CampaignRepository campaignRepository;

    private GetCampaignQueryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetCampaignQueryHandler(campaignRepository);
    }

    @Test
    void returns_campaign_by_id() {
        Campaign campaign = Campaign.create("WELCOME10", "Welcome", null,
                DiscountType.PERCENTAGE, new BigDecimal("10.00"), Set.of("TARIFF-A"),
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-12-31T00:00:00Z"),
                null, 1);
        when(campaignRepository.findById(campaign.getId())).thenReturn(Optional.of(campaign));

        CampaignResponse response = handler.handle(new GetCampaignQuery(campaign.getId()));

        assertThat(response.id()).isEqualTo(campaign.getId());
        assertThat(response.code()).isEqualTo("WELCOME10");
    }

    @Test
    void throws_not_found_for_unknown_id() {
        UUID id = UUID.randomUUID();
        when(campaignRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new GetCampaignQuery(id)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
