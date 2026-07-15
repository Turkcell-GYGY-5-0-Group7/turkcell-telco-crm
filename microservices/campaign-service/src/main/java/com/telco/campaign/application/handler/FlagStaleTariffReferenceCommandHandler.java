package com.telco.campaign.application.handler;

import com.telco.campaign.application.command.FlagStaleTariffReferenceCommand;
import com.telco.campaign.domain.model.Campaign;
import com.telco.campaign.domain.model.CampaignStatus;
import com.telco.campaign.infrastructure.persistence.CampaignRepository;
import com.telco.platform.cqrs.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Flags every ACTIVE campaign referencing {@code command.tariffCode()} (Feature 21.4.3), driven by the
 * {@code tariff.price-changed.v1} consumer. No effect (not an error) when no ACTIVE campaign
 * references the tariff code.
 */
@Component
public class FlagStaleTariffReferenceCommandHandler
        implements CommandHandler<FlagStaleTariffReferenceCommand, Void> {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(FlagStaleTariffReferenceCommandHandler.class);

    private final CampaignRepository campaignRepository;

    public FlagStaleTariffReferenceCommandHandler(CampaignRepository campaignRepository) {
        this.campaignRepository = campaignRepository;
    }

    @Override
    public Void handle(FlagStaleTariffReferenceCommand command) {
        List<Campaign> affected = campaignRepository.findByStatusAndApplicableTariffCode(
                CampaignStatus.ACTIVE, command.tariffCode());

        if (affected.isEmpty()) {
            LOGGER.debug("tariff code={} referenced by no ACTIVE campaign - no-op", command.tariffCode());
            return null;
        }

        for (Campaign campaign : affected) {
            campaign.flagStaleTariffReference(command.reason());
            campaignRepository.save(campaign);
            LOGGER.warn("Flagged campaign code={} id={} for stale tariff reference: tariffCode={} "
                            + "reason={}",
                    campaign.getCode(), campaign.getId(), command.tariffCode(), command.reason());
        }
        return null;
    }
}
