package com.telco.campaign.application.handler;

import com.telco.campaign.application.command.ReleaseRedemptionCommand;
import com.telco.campaign.domain.model.CampaignRedemption;
import com.telco.campaign.infrastructure.persistence.CampaignRedemptionRepository;
import com.telco.platform.common.exception.BusinessRuleException;
import com.telco.platform.cqrs.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Releases the {@code CampaignRedemption} for {@code command.orderId()} (RESERVED -&gt; RELEASED),
 * driven by the {@code order.cancelled.v1} consumer (Feature 21.4.2). Two-layer idempotency:
 * (1) {@code ReleaseRedemptionCommand} is an {@code IdempotentRequest} deduped by the mediator
 * {@code InboxBehavior}; (2) this handler is itself check-then-act - a redemption already RELEASED
 * (or CONFIRMED, or otherwise not RESERVED) is left untouched, and an {@code orderId} with no matching
 * redemption row at all is a silent no-op, not an error.
 */
@Component
public class ReleaseRedemptionCommandHandler
        implements CommandHandler<ReleaseRedemptionCommand, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReleaseRedemptionCommandHandler.class);

    private final CampaignRedemptionRepository campaignRedemptionRepository;

    public ReleaseRedemptionCommandHandler(CampaignRedemptionRepository campaignRedemptionRepository) {
        this.campaignRedemptionRepository = campaignRedemptionRepository;
    }

    @Override
    public Void handle(ReleaseRedemptionCommand command) {
        Optional<CampaignRedemption> redemptionOpt =
                campaignRedemptionRepository.findByOrderId(command.orderId());
        if (redemptionOpt.isEmpty()) {
            LOGGER.debug("order.cancelled.v1: no CampaignRedemption for orderId={} - no campaign was "
                    + "applied to this order, silent no-op", command.orderId());
            return null;
        }

        CampaignRedemption redemption = redemptionOpt.get();
        try {
            redemption.release();
        } catch (BusinessRuleException e) {
            // Already RELEASED (redelivery) or CONFIRMED (payment already settled before the
            // cancellation event arrived): not an error, no further transition is possible or needed.
            LOGGER.debug("Redemption {} for orderId={} already in status {} - skipping release: {}",
                    redemption.getId(), command.orderId(), redemption.getStatus(), e.getMessage());
            return null;
        }
        campaignRedemptionRepository.save(redemption);
        LOGGER.info("CampaignRedemption {} released for orderId={}", redemption.getId(), command.orderId());
        return null;
    }
}
