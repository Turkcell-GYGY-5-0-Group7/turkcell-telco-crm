package com.telco.campaign.application.handler;

import com.telco.campaign.application.command.PauseCampaignCommand;
import com.telco.campaign.application.dto.CampaignResponse;
import com.telco.campaign.domain.event.CampaignPausedEvent;
import com.telco.campaign.domain.model.Campaign;
import com.telco.campaign.infrastructure.persistence.CampaignRepository;
import com.telco.platform.common.exception.CommonErrorCode;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.outbox.OutboxService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Pauses a campaign (ACTIVE -&gt; PAUSED) and publishes {@code campaign.paused.v1} through the
 * transactional outbox, atomically with the status update (Feature 21.4.1, ADR-009). Illegal
 * transitions raise BusinessRuleException.
 */
@Component
public class PauseCampaignCommandHandler
        implements CommandHandler<PauseCampaignCommand, CampaignResponse> {

    private static final String AGGREGATE_TYPE = "campaign";
    private static final String EVENT_TYPE = "campaign.paused.v1";

    private final CampaignRepository campaignRepository;
    private final OutboxService outboxService;

    public PauseCampaignCommandHandler(CampaignRepository campaignRepository,
                                        OutboxService outboxService) {
        this.campaignRepository = campaignRepository;
        this.outboxService = outboxService;
    }

    @Override
    public CampaignResponse handle(PauseCampaignCommand command) {
        Campaign campaign = campaignRepository.findById(command.id())
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "Campaign not found with id: " + command.id(),
                        Map.of("id", command.id().toString())));

        campaign.pause();
        campaignRepository.save(campaign);

        outboxService.publish(
                AGGREGATE_TYPE,
                campaign.getId().toString(),
                EVENT_TYPE,
                new CampaignPausedEvent(
                        campaign.getId().toString(),
                        campaign.getCode(),
                        Instant.now().toString()
                )
        );

        return CampaignResponse.from(campaign);
    }
}
