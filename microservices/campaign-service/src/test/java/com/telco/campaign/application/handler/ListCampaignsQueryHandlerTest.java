package com.telco.campaign.application.handler;

import com.telco.campaign.application.query.ListCampaignsQuery;
import com.telco.campaign.domain.model.Campaign;
import com.telco.campaign.domain.model.DiscountType;
import com.telco.campaign.infrastructure.persistence.CampaignRepository;
import com.telco.platform.common.api.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListCampaignsQueryHandlerTest {

    @Mock private CampaignRepository campaignRepository;

    private ListCampaignsQueryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ListCampaignsQueryHandler(campaignRepository);
    }

    @Test
    void returns_paginated_campaigns() {
        Campaign campaign = Campaign.create("WELCOME10", "Welcome", null,
                DiscountType.PERCENTAGE, new BigDecimal("10.00"), Set.of("TARIFF-A"),
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-12-31T00:00:00Z"),
                null, 1);
        when(campaignRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(campaign), PageRequest.of(0, 20), 1));

        PageResult<?> result = handler.handle(new ListCampaignsQuery(0, 20));

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
    }
}
