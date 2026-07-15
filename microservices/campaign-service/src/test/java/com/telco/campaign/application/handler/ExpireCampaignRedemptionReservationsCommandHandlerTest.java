package com.telco.campaign.application.handler;

import com.telco.campaign.application.command.ExpireCampaignRedemptionReservationsCommand;
import com.telco.campaign.application.command.ExpireCampaignRedemptionReservationsResult;
import com.telco.campaign.domain.model.CampaignRedemption;
import com.telco.campaign.domain.model.RedemptionStatus;
import com.telco.campaign.infrastructure.persistence.CampaignRedemptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpireCampaignRedemptionReservationsCommandHandlerTest {

    @Mock private CampaignRedemptionRepository campaignRedemptionRepository;

    private ExpireCampaignRedemptionReservationsCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ExpireCampaignRedemptionReservationsCommandHandler(campaignRedemptionRepository);
    }

    @Test
    void releases_every_expired_reserved_redemption() {
        CampaignRedemption expired1 = CampaignRedemption.reserve(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now().plusSeconds(1));
        CampaignRedemption expired2 = CampaignRedemption.reserve(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now().plusSeconds(1));
        when(campaignRedemptionRepository.findByStatusAndReservedUntilBefore(
                eq(RedemptionStatus.RESERVED), any(Instant.class)))
                .thenReturn(List.of(expired1, expired2));

        ExpireCampaignRedemptionReservationsResult result =
                handler.handle(new ExpireCampaignRedemptionReservationsCommand());

        assertThat(result.releasedCount()).isEqualTo(2);
        assertThat(expired1.getStatus()).isEqualTo(RedemptionStatus.RELEASED);
        assertThat(expired2.getStatus()).isEqualTo(RedemptionStatus.RELEASED);
        verify(campaignRedemptionRepository).save(expired1);
        verify(campaignRedemptionRepository).save(expired2);
    }

    @Test
    void returns_zero_when_nothing_is_expired() {
        when(campaignRedemptionRepository.findByStatusAndReservedUntilBefore(
                eq(RedemptionStatus.RESERVED), any(Instant.class)))
                .thenReturn(List.of());

        ExpireCampaignRedemptionReservationsResult result =
                handler.handle(new ExpireCampaignRedemptionReservationsCommand());

        assertThat(result.releasedCount()).isZero();
    }
}
