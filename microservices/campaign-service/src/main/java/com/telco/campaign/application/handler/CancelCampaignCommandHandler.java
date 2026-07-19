package com.telco.campaign.application.handler;

import com.telco.campaign.application.command.CancelCampaignCommand;
import com.telco.campaign.application.dto.CampaignResponse;
import com.telco.campaign.domain.event.CampaignCancelledEvent;
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
 * Cancels a campaign (any non-terminal status -&gt; CANCELLED) and publishes
 * {@code campaign.cancelled.v1} through the transactional outbox, atomically with the status update
 * (Feature 21.4.1, ADR-009). Backs the {@code DELETE /api/v1/campaigns/{id}} endpoint - there is no
 * hard delete, only a terminal-status transition. Illegal transitions (already CANCELLED/EXPIRED)
 * raise BusinessRuleException.
 */
@Component
public class CancelCampaignCommandHandler
        implements CommandHandler<CancelCampaignCommand, CampaignResponse> {

    private static final String AGGREGATE_TYPE = "campaign";
    private static final String EVENT_TYPE = "campaign.cancelled.v1";

    private final CampaignRepository campaignRepository;
    private final OutboxService outboxService;

    public CancelCampaignCommandHandler(CampaignRepository campaignRepository,
                                         OutboxService outboxService) {
        this.campaignRepository = campaignRepository;
        this.outboxService = outboxService;
    }

    @Override
    public CampaignResponse handle(CancelCampaignCommand command) {
        Campaign campaign = campaignRepository.findById(command.id())
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "Campaign not found with id: " + command.id(),
                        Map.of("id", command.id().toString())));

        campaign.cancel();
        campaignRepository.save(campaign);

        outboxService.publish(
                AGGREGATE_TYPE,
                campaign.getId().toString(),
                EVENT_TYPE,
                new CampaignCancelledEvent(
                        campaign.getId().toString(),
                        campaign.getCode(),
                        Instant.now().toString()
                )
        );

        return CampaignResponse.from(campaign);
    }
}
