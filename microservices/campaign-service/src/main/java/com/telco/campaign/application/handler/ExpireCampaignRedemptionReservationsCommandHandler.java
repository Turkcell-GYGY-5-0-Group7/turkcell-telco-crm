package com.telco.campaign.application.handler;

import com.telco.campaign.application.command.ExpireCampaignRedemptionReservationsCommand;
import com.telco.campaign.application.command.ExpireCampaignRedemptionReservationsResult;
import com.telco.campaign.domain.model.CampaignRedemption;
import com.telco.campaign.domain.model.RedemptionStatus;
import com.telco.campaign.infrastructure.persistence.CampaignRedemptionRepository;
import com.telco.platform.cqrs.CommandHandler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Releases every expired {@code RESERVED} {@code CampaignRedemption} hold back to {@code RELEASED}
 * (Feature 21.4, ADR-027 Section 4 ratification amendment: not every {@code RESERVED} row resolves to
 * {@code CONFIRMED}/{@code RELEASED} via a real order/payment event - order-service has no
 * order-abandonment timeout, so a stranded hold would otherwise occupy a cap slot indefinitely),
 * driven through {@link CampaignRedemption#release()} - never a direct SQL update bypassing the
 * domain method's state-machine guard. Mirrors subscription-service's
 * {@code ExpireMsisdnReservationsCommandHandler} (Feature 17.3) exactly.
 *
 * <p>Runs inside the mediator {@code TransactionBehavior}'s transaction (invoked by
 * {@link com.telco.campaign.infrastructure.scheduler.CampaignRedemptionReservationExpiryReaper} under
 * a {@code DistributedLock}), so all releases in one sweep commit atomically.
 */
@Component
public class ExpireCampaignRedemptionReservationsCommandHandler implements
        CommandHandler<ExpireCampaignRedemptionReservationsCommand, ExpireCampaignRedemptionReservationsResult> {

    private final CampaignRedemptionRepository campaignRedemptionRepository;

    public ExpireCampaignRedemptionReservationsCommandHandler(
            CampaignRedemptionRepository campaignRedemptionRepository) {
        this.campaignRedemptionRepository = campaignRedemptionRepository;
    }

    @Override
    public ExpireCampaignRedemptionReservationsResult handle(
            ExpireCampaignRedemptionReservationsCommand command) {
        List<CampaignRedemption> expired = campaignRedemptionRepository
                .findByStatusAndReservedUntilBefore(RedemptionStatus.RESERVED, Instant.now());

        for (CampaignRedemption redemption : expired) {
            redemption.release();
            campaignRedemptionRepository.save(redemption);
        }

        return new ExpireCampaignRedemptionReservationsResult(expired.size());
    }
}
