package com.telco.campaign.application.handler;

import com.telco.campaign.application.command.CreateCampaignCommand;
import com.telco.campaign.application.dto.CampaignResponse;
import com.telco.campaign.domain.event.CampaignCreatedEvent;
import com.telco.campaign.domain.model.Campaign;
import com.telco.campaign.infrastructure.persistence.CampaignRepository;
import com.telco.platform.common.exception.BusinessRuleException;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.outbox.OutboxService;
import org.springframework.stereotype.Component;

/**
 * Creates a new campaign in DRAFT status and publishes {@code campaign.created.v1} through the
 * transactional outbox, atomically with the insert (Feature 21.4.1, ADR-009). The mediator
 * TransactionBehavior wraps this handler in a transaction so the JPA insert and outbox row commit
 * together.
 */
@Component
public class CreateCampaignCommandHandler implements CommandHandler<CreateCampaignCommand, CampaignResponse> {

    private static final String AGGREGATE_TYPE = "campaign";
    private static final String EVENT_TYPE = "campaign.created.v1";

    private final CampaignRepository campaignRepository;
    private final OutboxService outboxService;

    public CreateCampaignCommandHandler(CampaignRepository campaignRepository,
                                         OutboxService outboxService) {
        this.campaignRepository = campaignRepository;
        this.outboxService = outboxService;
    }

    @Override
    public CampaignResponse handle(CreateCampaignCommand command) {
        if (campaignRepository.existsByCode(command.code())) {
            throw new BusinessRuleException(
                    "Campaign with code '" + command.code() + "' already exists");
        }

        Campaign campaign = Campaign.create(
                command.code(),
                command.name(),
                command.description(),
                command.discountType(),
                command.discountValue(),
                command.applicableTariffCodes(),
                command.validFrom(),
                command.validTo(),
                command.totalRedemptionCap(),
                command.perCustomerRedemptionCap()
        );

        campaignRepository.save(campaign);

        outboxService.publish(
                AGGREGATE_TYPE,
                campaign.getId().toString(),
                EVENT_TYPE,
                new CampaignCreatedEvent(
                        campaign.getId().toString(),
                        campaign.getCode(),
                        campaign.getName(),
                        campaign.getDiscountType().name(),
                        campaign.getDiscountValue(),
                        campaign.getValidFrom().toString(),
                        campaign.getValidTo().toString(),
                        campaign.getTotalRedemptionCap(),
                        campaign.getPerCustomerRedemptionCap(),
                        campaign.getCreatedAt().toString()
                )
        );

        return CampaignResponse.from(campaign);
    }
}
