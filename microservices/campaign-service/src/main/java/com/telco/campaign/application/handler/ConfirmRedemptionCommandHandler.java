package com.telco.campaign.application.handler;

import com.telco.campaign.application.command.ConfirmRedemptionCommand;
import com.telco.campaign.domain.model.CampaignRedemption;
import com.telco.campaign.infrastructure.persistence.CampaignRedemptionRepository;
import com.telco.platform.common.exception.BusinessRuleException;
import com.telco.platform.cqrs.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Confirms the {@code CampaignRedemption} for {@code command.orderId()} (RESERVED -&gt; CONFIRMED),
 * driven by the {@code payment.completed.v1} consumer (Feature 21.4.2, ADR-027 Section 4
 * ratification). Two-layer idempotency: (1) {@code ConfirmRedemptionCommand} is an
 * {@code IdempotentRequest} deduped by the mediator {@code InboxBehavior}; (2) this handler is itself
 * check-then-act - a redemption already CONFIRMED (or otherwise not RESERVED) is left untouched, and
 * an {@code orderId} with no matching redemption row at all is a silent no-op, not an error (the order
 * had no campaign applied).
 */
@Component
public class ConfirmRedemptionCommandHandler
        implements CommandHandler<ConfirmRedemptionCommand, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfirmRedemptionCommandHandler.class);

    private final CampaignRedemptionRepository campaignRedemptionRepository;

    public ConfirmRedemptionCommandHandler(CampaignRedemptionRepository campaignRedemptionRepository) {
        this.campaignRedemptionRepository = campaignRedemptionRepository;
    }

    @Override
    public Void handle(ConfirmRedemptionCommand command) {
        Optional<CampaignRedemption> redemptionOpt =
                campaignRedemptionRepository.findByOrderId(command.orderId());
        if (redemptionOpt.isEmpty()) {
            LOGGER.debug("payment.completed.v1: no CampaignRedemption for orderId={} - no campaign "
                    + "was applied to this order, silent no-op", command.orderId());
            return null;
        }

        CampaignRedemption redemption = redemptionOpt.get();
        try {
            redemption.confirm();
        } catch (BusinessRuleException e) {
            // Already CONFIRMED (redelivery) or in a status that cannot be confirmed: not an error,
            // the redemption is already in its terminal-for-this-transition state.
            LOGGER.debug("Redemption {} for orderId={} already in status {} - skipping confirm: {}",
                    redemption.getId(), command.orderId(), redemption.getStatus(), e.getMessage());
            return null;
        }
        campaignRedemptionRepository.save(redemption);
        LOGGER.info("CampaignRedemption {} confirmed for orderId={}", redemption.getId(), command.orderId());
        return null;
    }
}
