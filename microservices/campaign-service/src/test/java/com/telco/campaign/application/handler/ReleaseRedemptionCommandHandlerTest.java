package com.telco.campaign.application.handler;

import com.telco.campaign.application.command.ReleaseRedemptionCommand;
import com.telco.campaign.domain.model.CampaignRedemption;
import com.telco.campaign.domain.model.RedemptionStatus;
import com.telco.campaign.infrastructure.persistence.CampaignRedemptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReleaseRedemptionCommandHandlerTest {

    @Mock private CampaignRedemptionRepository campaignRedemptionRepository;

    private ReleaseRedemptionCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ReleaseRedemptionCommandHandler(campaignRedemptionRepository);
    }

    private static CampaignRedemption reservedRedemption(UUID orderId) {
        return CampaignRedemption.reserve(UUID.randomUUID(), UUID.randomUUID(), orderId,
                Instant.now().plusSeconds(3600));
    }

    @Test
    void releases_reserved_redemption() {
        UUID orderId = UUID.randomUUID();
        CampaignRedemption redemption = reservedRedemption(orderId);
        when(campaignRedemptionRepository.findByOrderId(orderId)).thenReturn(Optional.of(redemption));

        handler.handle(new ReleaseRedemptionCommand(orderId, "msg-1"));

        assertThat(redemption.getStatus()).isEqualTo(RedemptionStatus.RELEASED);
        verify(campaignRedemptionRepository).save(redemption);
    }

    @Test
    void no_matching_redemption_is_a_silent_no_op() {
        UUID orderId = UUID.randomUUID();
        when(campaignRedemptionRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        handler.handle(new ReleaseRedemptionCommand(orderId, "msg-2"));

        verify(campaignRedemptionRepository, never()).save(any());
    }

    @Test
    void confirmed_redemption_is_not_released_by_redelivery() {
        UUID orderId = UUID.randomUUID();
        CampaignRedemption redemption = reservedRedemption(orderId);
        redemption.confirm();
        when(campaignRedemptionRepository.findByOrderId(orderId)).thenReturn(Optional.of(redemption));

        handler.handle(new ReleaseRedemptionCommand(orderId, "msg-3"));

        assertThat(redemption.getStatus()).isEqualTo(RedemptionStatus.CONFIRMED);
        verify(campaignRedemptionRepository, never()).save(any());
    }
}
