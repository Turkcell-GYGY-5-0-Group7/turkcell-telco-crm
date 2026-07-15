package com.telco.campaign.domain.model;

import com.telco.platform.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CampaignTest {

    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-12-31T00:00:00Z");

    private static Campaign draftCampaign() {
        return Campaign.create("WELCOME10", "Welcome 10%", "10% off for new customers",
                DiscountType.PERCENTAGE, new BigDecimal("10.00"),
                Set.of("TARIFF-A"), FROM, TO, 1000, 1);
    }

    @Test
    void create_initialises_draft_status() {
        Campaign campaign = draftCampaign();

        assertThat(campaign.getId()).isNotNull();
        assertThat(campaign.getCode()).isEqualTo("WELCOME10");
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.DRAFT);
        assertThat(campaign.getApplicableTariffCodes()).containsExactly("TARIFF-A");
    }

    @Test
    void create_rejects_validTo_before_validFrom() {
        assertThatThrownBy(() -> Campaign.create("X", "X", null,
                DiscountType.FIXED_AMOUNT, BigDecimal.TEN, Set.of("T"), TO, FROM, null, 1))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void activate_transitions_draft_to_active() {
        Campaign campaign = draftCampaign();

        campaign.activate();

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.ACTIVE);
    }

    @Test
    void activate_transitions_paused_to_active() {
        Campaign campaign = draftCampaign();
        campaign.activate();
        campaign.pause();

        campaign.activate();

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.ACTIVE);
    }

    @Test
    void activate_rejects_cancelled_campaign() {
        Campaign campaign = draftCampaign();
        campaign.cancel();

        assertThatThrownBy(campaign::activate).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void activate_rejects_expired_campaign() {
        Campaign campaign = draftCampaign();
        campaign.activate();
        campaign.expire();

        assertThatThrownBy(campaign::activate).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void pause_rejects_non_active_campaign() {
        Campaign campaign = draftCampaign();

        assertThatThrownBy(campaign::pause).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void pause_transitions_active_to_paused() {
        Campaign campaign = draftCampaign();
        campaign.activate();

        campaign.pause();

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.PAUSED);
    }

    @Test
    void cancel_transitions_draft_to_cancelled() {
        Campaign campaign = draftCampaign();

        campaign.cancel();

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.CANCELLED);
    }

    @Test
    void cancel_transitions_active_to_cancelled() {
        Campaign campaign = draftCampaign();
        campaign.activate();

        campaign.cancel();

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.CANCELLED);
    }

    @Test
    void cancel_rejects_already_cancelled_campaign() {
        Campaign campaign = draftCampaign();
        campaign.cancel();

        assertThatThrownBy(campaign::cancel).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void cancel_rejects_expired_campaign() {
        Campaign campaign = draftCampaign();
        campaign.activate();
        campaign.expire();

        assertThatThrownBy(campaign::cancel).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void expire_transitions_active_to_expired() {
        Campaign campaign = draftCampaign();
        campaign.activate();

        campaign.expire();

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.EXPIRED);
    }

    @Test
    void expire_transitions_paused_to_expired() {
        Campaign campaign = draftCampaign();
        campaign.activate();
        campaign.pause();

        campaign.expire();

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.EXPIRED);
    }

    @Test
    void expire_rejects_draft_campaign() {
        Campaign campaign = draftCampaign();

        assertThatThrownBy(campaign::expire).isInstanceOf(BusinessRuleException.class);
    }
}
