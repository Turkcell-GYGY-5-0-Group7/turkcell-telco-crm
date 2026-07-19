package com.telco.campaign.application.handler;

import com.telco.campaign.application.command.LogStaleTariffReferenceCommand;
import com.telco.campaign.domain.model.Campaign;
import com.telco.campaign.domain.model.CampaignStatus;
import com.telco.campaign.infrastructure.persistence.CampaignRepository;
import com.telco.platform.cqrs.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Logs a WARN when {@code command.tariffCode()} is referenced by an ACTIVE campaign
 * (Feature 21.4.3, {@code tariff.created.v1} consumer). Read-only: never mutates a {@link Campaign}.
 */
@Component
public class LogStaleTariffReferenceCommandHandler
        implements CommandHandler<LogStaleTariffReferenceCommand, Void> {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(LogStaleTariffReferenceCommandHandler.class);

    private final CampaignRepository campaignRepository;

    public LogStaleTariffReferenceCommandHandler(CampaignRepository campaignRepository) {
        this.campaignRepository = campaignRepository;
    }

    @Override
    public Void handle(LogStaleTariffReferenceCommand command) {
        List<Campaign> affected = campaignRepository.findByStatusAndApplicableTariffCode(
                CampaignStatus.ACTIVE, command.tariffCode());

        if (affected.isEmpty()) {
            LOGGER.debug("tariff.created.v1: code={} referenced by no ACTIVE campaign - no-op",
                    command.tariffCode());
            return null;
        }

        for (Campaign campaign : affected) {
            LOGGER.warn("tariff.created.v1: tariff code={} referenced by ACTIVE campaign code={} id={} "
                            + "was (re)created - verify this is not an unintended tariff-code reuse",
                    command.tariffCode(), campaign.getCode(), campaign.getId());
        }
        return null;
    }
}
