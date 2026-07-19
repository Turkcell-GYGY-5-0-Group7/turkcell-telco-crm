package com.telco.campaign.domain.service;

import com.telco.campaign.domain.model.Campaign;
import com.telco.campaign.domain.model.CampaignRedemption;
import com.telco.campaign.domain.model.CampaignStatus;
import com.telco.campaign.domain.model.DiscountType;
import com.telco.campaign.domain.model.EligibilityDecision;
import com.telco.campaign.domain.model.EligibilityReason;
import com.telco.campaign.domain.model.RedemptionStatus;
import com.telco.campaign.infrastructure.persistence.CampaignRedemptionRepository;
import com.telco.campaign.infrastructure.persistence.CampaignRepository;
import com.telco.platform.common.exception.BusinessRuleException;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignEligibilityServiceTest {

    private static final String CAMPAIGN_CODE = "WELCOME10";
    private static final String TARIFF_CODE = "TARIFF-A";

    @Mock private CampaignRepository campaignRepository;
    @Mock private CampaignRedemptionRepository campaignRedemptionRepository;
    @Mock private OutboxService outboxService;

    private CampaignEligibilityService service;

    @BeforeEach
    void setUp() {
        service = new CampaignEligibilityService(
                campaignRepository, campaignRedemptionRepository, outboxService);
    }

    private static Campaign activeCampaign(Instant validFrom, Instant validTo,
                                            Integer totalCap, int perCustomerCap) {
        Campaign campaign = Campaign.create(CAMPAIGN_CODE, "Welcome", null,
                DiscountType.PERCENTAGE, new BigDecimal("10.00"),
                Set.of(TARIFF_CODE), validFrom, validTo, totalCap, perCustomerCap);
        campaign.activate();
        return campaign;
    }

    @Test
    void evaluate_returns_campaign_not_found_when_code_unknown() {
        when(campaignRepository.findByCode(CAMPAIGN_CODE)).thenReturn(Optional.empty());

        EligibilityDecision decision = service.evaluate(CAMPAIGN_CODE, UUID.randomUUID(), TARIFF_CODE);

        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reason()).isEqualTo(EligibilityReason.CAMPAIGN_NOT_FOUND);
    }

    @Test
    void evaluate_returns_not_yet_active_when_validFrom_is_future() {
        Instant now = Instant.now();
        Campaign campaign = activeCampaign(now.plus(1, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS),
                null, 1);
        when(campaignRepository.findByCode(CAMPAIGN_CODE)).thenReturn(Optional.of(campaign));

        EligibilityDecision decision = service.evaluate(CAMPAIGN_CODE, UUID.randomUUID(), TARIFF_CODE);

        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reason()).isEqualTo(EligibilityReason.NOT_YET_ACTIVE);
    }

    @Test
    void evaluate_returns_expired_and_auto_expires_when_validTo_is_past() {
        Instant now = Instant.now();
        Campaign campaign = activeCampaign(now.minus(30, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS),
                null, 1);
        when(campaignRepository.findByCode(CAMPAIGN_CODE)).thenReturn(Optional.of(campaign));

        EligibilityDecision decision = service.evaluate(CAMPAIGN_CODE, UUID.randomUUID(), TARIFF_CODE);

        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reason()).isEqualTo(EligibilityReason.EXPIRED);
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.EXPIRED);
        verify(campaignRepository).save(campaign);
        verify(outboxService).publish(eq("campaign"), eq(campaign.getId().toString()),
                eq("campaign.expired.v1"), any());
    }

    @Test
    void evaluate_returns_not_active_status_for_draft_campaign() {
        Instant now = Instant.now();
        Campaign campaign = Campaign.create(CAMPAIGN_CODE, "Welcome", null,
                DiscountType.PERCENTAGE, new BigDecimal("10.00"),
                Set.of(TARIFF_CODE), now.minus(1, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS),
                null, 1);
        when(campaignRepository.findByCode(CAMPAIGN_CODE)).thenReturn(Optional.of(campaign));

        EligibilityDecision decision = service.evaluate(CAMPAIGN_CODE, UUID.randomUUID(), TARIFF_CODE);

        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reason()).isEqualTo(EligibilityReason.NOT_ACTIVE_STATUS);
        verify(campaignRepository, never()).save(any());
    }

    @Test
    void evaluate_returns_tariff_not_applicable_when_code_not_targeted() {
        Instant now = Instant.now();
        Campaign campaign = activeCampaign(now.minus(1, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS),
                null, 1);
        when(campaignRepository.findByCode(CAMPAIGN_CODE)).thenReturn(Optional.of(campaign));

        EligibilityDecision decision = service.evaluate(CAMPAIGN_CODE, UUID.randomUUID(), "OTHER-TARIFF");

        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reason()).isEqualTo(EligibilityReason.TARIFF_NOT_APPLICABLE);
    }

    @Test
    void evaluate_returns_per_customer_cap_exceeded_when_customer_at_cap() {
        Instant now = Instant.now();
        Campaign campaign = activeCampaign(now.minus(1, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS),
                null, 1);
        UUID customerId = UUID.randomUUID();
        when(campaignRepository.findByCode(CAMPAIGN_CODE)).thenReturn(Optional.of(campaign));
        when(campaignRedemptionRepository.countByCampaignIdAndCustomerIdAndStatusIn(
                eq(campaign.getId()), eq(customerId), anyCollection())).thenReturn(1L);

        EligibilityDecision decision = service.evaluate(CAMPAIGN_CODE, customerId, TARIFF_CODE);

        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reason()).isEqualTo(EligibilityReason.PER_CUSTOMER_CAP_EXCEEDED);
    }

    @Test
    void evaluate_returns_total_cap_exceeded_when_campaign_at_total_cap() {
        Instant now = Instant.now();
        Campaign campaign = activeCampaign(now.minus(1, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS),
                5, 10);
        UUID customerId = UUID.randomUUID();
        when(campaignRepository.findByCode(CAMPAIGN_CODE)).thenReturn(Optional.of(campaign));
        when(campaignRedemptionRepository.countByCampaignIdAndCustomerIdAndStatusIn(
                eq(campaign.getId()), eq(customerId), anyCollection())).thenReturn(0L);
        when(campaignRedemptionRepository.countByCampaignIdAndStatusIn(
                eq(campaign.getId()), anyCollection())).thenReturn(5L);

        EligibilityDecision decision = service.evaluate(CAMPAIGN_CODE, customerId, TARIFF_CODE);

        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reason()).isEqualTo(EligibilityReason.TOTAL_CAP_EXCEEDED);
    }

    @Test
    void evaluate_never_blocks_on_total_cap_when_null_unlimited() {
        Instant now = Instant.now();
        Campaign campaign = activeCampaign(now.minus(1, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS),
                null, 10);
        UUID customerId = UUID.randomUUID();
        when(campaignRepository.findByCode(CAMPAIGN_CODE)).thenReturn(Optional.of(campaign));
        when(campaignRedemptionRepository.countByCampaignIdAndCustomerIdAndStatusIn(
                eq(campaign.getId()), eq(customerId), anyCollection())).thenReturn(0L);

        EligibilityDecision decision = service.evaluate(CAMPAIGN_CODE, customerId, TARIFF_CODE);

        assertThat(decision.eligible()).isTrue();
        verify(campaignRedemptionRepository, never()).countByCampaignIdAndStatusIn(any(), anyCollection());
    }

    @Test
    void evaluate_returns_eligible_with_discount_when_all_checks_pass() {
        Instant now = Instant.now();
        Campaign campaign = activeCampaign(now.minus(1, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS),
                100, 1);
        UUID customerId = UUID.randomUUID();
        when(campaignRepository.findByCode(CAMPAIGN_CODE)).thenReturn(Optional.of(campaign));
        when(campaignRedemptionRepository.countByCampaignIdAndCustomerIdAndStatusIn(
                eq(campaign.getId()), eq(customerId), anyCollection())).thenReturn(0L);
        when(campaignRedemptionRepository.countByCampaignIdAndStatusIn(
                eq(campaign.getId()), anyCollection())).thenReturn(0L);

        EligibilityDecision decision = service.evaluate(CAMPAIGN_CODE, customerId, TARIFF_CODE);

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.campaignId()).isEqualTo(campaign.getId());
        assertThat(decision.discountType()).isEqualTo(DiscountType.PERCENTAGE);
        assertThat(decision.discountValue()).isEqualByComparingTo("10.00");
        assertThat(decision.reason()).isNull();
    }

    @Test
    void reserve_throws_resource_not_found_when_campaign_missing() {
        UUID campaignId = UUID.randomUUID();
        when(campaignRepository.findByIdForUpdate(campaignId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reserve(campaignId, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reserve_throws_business_rule_exception_when_per_customer_cap_exceeded() {
        Instant now = Instant.now();
        Campaign campaign = activeCampaign(now.minus(1, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS),
                null, 1);
        UUID customerId = UUID.randomUUID();
        when(campaignRepository.findByIdForUpdate(campaign.getId())).thenReturn(Optional.of(campaign));
        when(campaignRedemptionRepository.countByCampaignIdAndCustomerIdAndStatusIn(
                eq(campaign.getId()), eq(customerId), anyCollection())).thenReturn(1L);

        assertThatThrownBy(() -> service.reserve(campaign.getId(), customerId, UUID.randomUUID()))
                .isInstanceOf(BusinessRuleException.class);
        verify(campaignRedemptionRepository, never()).save(any());
    }

    @Test
    void reserve_saves_new_redemption_when_under_cap() {
        Instant now = Instant.now();
        Campaign campaign = activeCampaign(now.minus(1, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS),
                10, 1);
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(campaignRepository.findByIdForUpdate(campaign.getId())).thenReturn(Optional.of(campaign));
        when(campaignRedemptionRepository.countByCampaignIdAndCustomerIdAndStatusIn(
                eq(campaign.getId()), eq(customerId), anyCollection())).thenReturn(0L);
        when(campaignRedemptionRepository.countByCampaignIdAndStatusIn(
                eq(campaign.getId()), anyCollection())).thenReturn(0L);
        when(campaignRedemptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CampaignRedemption redemption = service.reserve(campaign.getId(), customerId, orderId);

        assertThat(redemption.getStatus()).isEqualTo(RedemptionStatus.RESERVED);
        assertThat(redemption.getCampaignId()).isEqualTo(campaign.getId());
        assertThat(redemption.getCustomerId()).isEqualTo(customerId);
        assertThat(redemption.getOrderId()).isEqualTo(orderId);
    }

    /** Guards against an accidental collection-type drift breaking the derived query signature. */
    @Test
    void capCheck_uses_collection_of_statuses_argument() {
        Instant now = Instant.now();
        Campaign campaign = activeCampaign(now.minus(1, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS),
                10, 1);
        UUID customerId = UUID.randomUUID();
        when(campaignRepository.findByCode(CAMPAIGN_CODE)).thenReturn(Optional.of(campaign));
        when(campaignRedemptionRepository.countByCampaignIdAndCustomerIdAndStatusIn(
                any(), any(), any())).thenReturn(0L);
        when(campaignRedemptionRepository.countByCampaignIdAndStatusIn(any(), any())).thenReturn(0L);

        service.evaluate(CAMPAIGN_CODE, customerId, TARIFF_CODE);

        verify(campaignRedemptionRepository).countByCampaignIdAndCustomerIdAndStatusIn(
                eq(campaign.getId()), eq(customerId), any(Collection.class));
    }
}
