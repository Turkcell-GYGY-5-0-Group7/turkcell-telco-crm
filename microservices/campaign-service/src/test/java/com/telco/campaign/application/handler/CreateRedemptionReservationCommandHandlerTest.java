package com.telco.campaign.application.handler;

import com.telco.campaign.application.command.CreateRedemptionReservationCommand;
import com.telco.campaign.domain.model.CampaignRedemption;
import com.telco.campaign.domain.service.CampaignEligibilityService;
import com.telco.platform.common.exception.BusinessRuleException;
import com.telco.platform.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateRedemptionReservationCommandHandlerTest {

    @Mock private CampaignEligibilityService campaignEligibilityService;

    private CreateRedemptionReservationCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CreateRedemptionReservationCommandHandler(campaignEligibilityService);
    }

    @Test
    void reserves_via_eligibility_service() {
        UUID campaignId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(campaignEligibilityService.reserve(campaignId, customerId, orderId))
                .thenReturn(CampaignRedemption.reserve(campaignId, customerId, orderId,
                        Instant.now().plusSeconds(3600)));

        handler.handle(new CreateRedemptionReservationCommand(
                campaignId, customerId, orderId, "msg-1:" + campaignId));

        verify(campaignEligibilityService).reserve(campaignId, customerId, orderId);
    }

    @Test
    void swallows_cap_exceeded_business_rule_exception_without_throwing() {
        UUID campaignId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(campaignEligibilityService.reserve(campaignId, customerId, orderId))
                .thenThrow(new BusinessRuleException("cap exceeded"));

        // Must not throw: this is a known, accepted race outcome, not an error.
        handler.handle(new CreateRedemptionReservationCommand(
                campaignId, customerId, orderId, "msg-2:" + campaignId));
    }

    @Test
    void swallows_resource_not_found_without_throwing() {
        UUID campaignId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(campaignEligibilityService.reserve(any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("campaign missing"));

        handler.handle(new CreateRedemptionReservationCommand(
                campaignId, customerId, orderId, "msg-3:" + campaignId));
    }
}
