package com.telco.campaign.domain.model;

import com.telco.platform.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CampaignRedemptionTest {

    private static CampaignRedemption reserved() {
        return CampaignRedemption.reserve(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Instant.now().plusSeconds(3600));
    }

    @Test
    void reserve_creates_row_in_reserved_status() {
        CampaignRedemption redemption = reserved();

        assertThat(redemption.getId()).isNotNull();
        assertThat(redemption.getStatus()).isEqualTo(RedemptionStatus.RESERVED);
        assertThat(redemption.getRedeemedAt()).isNotNull();
        assertThat(redemption.getConfirmedAt()).isNull();
        assertThat(redemption.getReservedUntil()).isNotNull();
    }

    @Test
    void reserve_rejects_reservedUntil_in_the_past() {
        UUID campaignId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        assertThatThrownBy(() -> CampaignRedemption.reserve(
                campaignId, customerId, orderId, Instant.now().minusSeconds(1)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void confirm_transitions_reserved_to_confirmed() {
        CampaignRedemption redemption = reserved();

        redemption.confirm();

        assertThat(redemption.getStatus()).isEqualTo(RedemptionStatus.CONFIRMED);
        assertThat(redemption.getConfirmedAt()).isNotNull();
        assertThat(redemption.getReservedUntil()).isNull();
    }

    @Test
    void confirm_rejects_already_confirmed_redemption() {
        CampaignRedemption redemption = reserved();
        redemption.confirm();

        assertThatThrownBy(redemption::confirm).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void release_transitions_reserved_to_released() {
        CampaignRedemption redemption = reserved();

        redemption.release();

        assertThat(redemption.getStatus()).isEqualTo(RedemptionStatus.RELEASED);
        assertThat(redemption.getReservedUntil()).isNull();
    }

    @Test
    void release_rejects_confirmed_redemption() {
        CampaignRedemption redemption = reserved();
        redemption.confirm();

        assertThatThrownBy(redemption::release).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void release_rejects_already_released_redemption() {
        CampaignRedemption redemption = reserved();
        redemption.release();

        assertThatThrownBy(redemption::release).isInstanceOf(BusinessRuleException.class);
    }
}
