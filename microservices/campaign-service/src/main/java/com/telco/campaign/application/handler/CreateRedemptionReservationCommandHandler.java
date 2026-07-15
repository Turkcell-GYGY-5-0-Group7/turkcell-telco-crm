package com.telco.campaign.application.handler;

import com.telco.campaign.application.command.CreateRedemptionReservationCommand;
import com.telco.campaign.domain.service.CampaignEligibilityService;
import com.telco.platform.common.exception.BusinessRuleException;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reserves a redemption slot for one campaign-priced {@code order.created.v1} line item by delegating
 * to {@link CampaignEligibilityService#reserve} (Feature 21.4.3), which locks the {@code Campaign} row
 * ({@code PESSIMISTIC_WRITE}) for the duration of the count-then-write decision, so concurrent
 * {@code order.created.v1} events for the same campaign cannot both reserve past the cap
 * (Feature 21.2.2's race-safety proof, now real end to end through the event path).
 *
 * <p>A cap-exceeded (or campaign-missing) outcome at reservation time is a genuine, expected business
 * race - the synchronous {@code /internal/campaigns/validate} call at order-creation time is a pure
 * read with no lock, so a slot that looked free at validation time can be gone by the time
 * {@code order.created.v1} is consumed. Propagating that failure would poison-loop the Kafka listener
 * (the same command would deterministically fail again on every redelivery), so it is logged as a
 * WARN and swallowed here rather than rethrown: the order was already created and priced at the
 * discount, but no redemption row is recorded, so the cap is never exceeded even though the discount
 * was, in this rare race, given away. This inherent limitation of a fail-open, non-transactional
 * synchronous read is a known, accepted tradeoff of ADR-027 Decision Section 4, not a bug in this
 * handler.
 */
@Component
public class CreateRedemptionReservationCommandHandler
        implements CommandHandler<CreateRedemptionReservationCommand, Void> {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(CreateRedemptionReservationCommandHandler.class);

    private final CampaignEligibilityService campaignEligibilityService;

    public CreateRedemptionReservationCommandHandler(
            CampaignEligibilityService campaignEligibilityService) {
        this.campaignEligibilityService = campaignEligibilityService;
    }

    @Override
    public Void handle(CreateRedemptionReservationCommand command) {
        try {
            campaignEligibilityService.reserve(
                    command.campaignId(), command.customerId(), command.orderId());
            LOGGER.info("Reserved CampaignRedemption for campaignId={} customerId={} orderId={}",
                    command.campaignId(), command.customerId(), command.orderId());
        } catch (BusinessRuleException e) {
            LOGGER.warn("Could not reserve CampaignRedemption for campaignId={} customerId={} "
                            + "orderId={}: {} (order already priced with the discount; cap-safety is "
                            + "preserved by recording no redemption)",
                    command.campaignId(), command.customerId(), command.orderId(), e.getMessage());
        } catch (ResourceNotFoundException e) {
            LOGGER.warn("Could not reserve CampaignRedemption: campaignId={} no longer exists "
                    + "(customerId={} orderId={}): {}",
                    command.campaignId(), command.customerId(), command.orderId(), e.getMessage());
        }
        return null;
    }
}
