package com.telco.campaign.application.handler;

import com.telco.campaign.application.dto.CampaignValidationResponse;
import com.telco.campaign.application.query.ValidateCampaignQuery;
import com.telco.campaign.domain.model.Campaign;
import com.telco.campaign.domain.model.CampaignStatus;
import com.telco.campaign.domain.model.DiscountType;
import com.telco.campaign.domain.model.CampaignRedemption;
import com.telco.campaign.domain.service.CampaignEligibilityService;
import com.telco.campaign.infrastructure.persistence.CampaignRedemptionRepository;
import com.telco.campaign.infrastructure.persistence.CampaignRepository;
import com.telco.platform.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ValidateCampaignQueryHandler} (Feature 21.3.1). Exercises the
 * explicit-{@code campaignCode} path (straight delegation to {@link CampaignEligibilityService})
 * and the omitted-{@code campaignCode} auto-resolution path (tie-break: highest
 * {@code discountValue} among ACTIVE, tariff-applicable candidates), plus the read-only guarantee
 * (never touches {@link CampaignRedemptionRepository}).
 */
@ExtendWith(MockitoExtension.class)
class ValidateCampaignQueryHandlerTest {

    @Mock private CampaignRepository campaignRepository;
    @Mock private CampaignRedemptionRepository campaignRedemptionRepository;
    @Mock private OutboxService outboxService;

    private CampaignEligibilityService eligibilityService;
    private ValidateCampaignQueryHandler handler;

    private static final UUID CUSTOMER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        eligibilityService = new CampaignEligibilityService(
                campaignRepository, campaignRedemptionRepository, outboxService);
        handler = new ValidateCampaignQueryHandler(eligibilityService, campaignRepository);
    }

    private static Campaign activeCampaign(String code, DiscountType type, String discountValue) {
        return Campaign.create(code, code + " name", null, type, new BigDecimal(discountValue),
                Set.of("TARIFF-A"), Instant.parse("2020-01-01T00:00:00Z"),
                Instant.parse("2999-01-01T00:00:00Z"), 100, 100);
    }

    @Test
    void explicit_campaign_code_delegates_straight_to_eligibility_service() {
        Campaign campaign = activeCampaign("SUMMER25", DiscountType.PERCENTAGE, "25.00");
        campaign.activate();
        when(campaignRepository.findByCode("SUMMER25")).thenReturn(Optional.of(campaign));
        when(campaignRedemptionRepository.countByCampaignIdAndCustomerIdAndStatusIn(any(), any(), anySet()))
                .thenReturn(0L);

        CampaignValidationResponse response =
                handler.handle(new ValidateCampaignQuery(CUSTOMER_ID, "TARIFF-A", "SUMMER25"));

        assertThat(response.eligible()).isTrue();
        assertThat(response.campaignId()).isEqualTo(campaign.getId());
        assertThat(response.discountType()).isEqualTo("PERCENTAGE");
        assertThat(response.discountValue()).isEqualByComparingTo("25.00");
        assertThat(response.reason()).isNull();
        verify(campaignRepository, never()).findByStatusAndApplicableTariffCode(any(), any());
    }

    @Test
    void explicit_campaign_code_returns_ineligible_with_specific_reason() {
        when(campaignRepository.findByCode("UNKNOWN")).thenReturn(Optional.empty());

        CampaignValidationResponse response =
                handler.handle(new ValidateCampaignQuery(CUSTOMER_ID, "TARIFF-A", "UNKNOWN"));

        assertThat(response.eligible()).isFalse();
        assertThat(response.reason()).isEqualTo("CAMPAIGN_NOT_FOUND");
        assertThat(response.campaignId()).isNull();
        assertThat(response.discountType()).isNull();
        assertThat(response.discountValue()).isNull();
    }

    @Test
    void omitted_campaign_code_auto_resolves_best_matching_active_campaign() {
        Campaign resolved = activeCampaign("AUTO10", DiscountType.FIXED_AMOUNT, "10.00");
        resolved.activate();
        when(campaignRepository.findByStatusAndApplicableTariffCode(CampaignStatus.ACTIVE, "TARIFF-A"))
                .thenReturn(List.of(resolved));
        when(campaignRepository.findByCode("AUTO10")).thenReturn(Optional.of(resolved));
        when(campaignRedemptionRepository.countByCampaignIdAndCustomerIdAndStatusIn(any(), any(), anySet()))
                .thenReturn(0L);

        CampaignValidationResponse response =
                handler.handle(new ValidateCampaignQuery(CUSTOMER_ID, "TARIFF-A", null));

        assertThat(response.eligible()).isTrue();
        assertThat(response.campaignId()).isEqualTo(resolved.getId());
        assertThat(response.discountType()).isEqualTo("FIXED_AMOUNT");
    }

    @Test
    void omitted_campaign_code_with_no_active_match_returns_no_matching_campaign_reason() {
        when(campaignRepository.findByStatusAndApplicableTariffCode(CampaignStatus.ACTIVE, "TARIFF-Z"))
                .thenReturn(List.of());

        CampaignValidationResponse response =
                handler.handle(new ValidateCampaignQuery(CUSTOMER_ID, "TARIFF-Z", null));

        assertThat(response.eligible()).isFalse();
        assertThat(response.reason()).isEqualTo("NO_MATCHING_CAMPAIGN");
        verify(campaignRepository, never()).findByCode(any());
    }

    @Test
    void never_creates_or_mutates_a_campaign_redemption_row() {
        Campaign campaign = activeCampaign("SUMMER25", DiscountType.PERCENTAGE, "25.00");
        campaign.activate();
        when(campaignRepository.findByCode("SUMMER25")).thenReturn(Optional.of(campaign));
        when(campaignRedemptionRepository.countByCampaignIdAndCustomerIdAndStatusIn(any(), any(), anySet()))
                .thenReturn(0L);

        handler.handle(new ValidateCampaignQuery(CUSTOMER_ID, "TARIFF-A", "SUMMER25"));

        verify(campaignRedemptionRepository, never()).save(any(CampaignRedemption.class));
    }
}
