package com.telco.campaign.infrastructure.persistence;

import com.telco.campaign.domain.model.CampaignRedemption;
import com.telco.campaign.domain.model.RedemptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link CampaignRedemption}. Cap-counting queries (per-customer and
 * total, counting CONFIRMED + still-live RESERVED rows, Feature 21.2.2) are backed by the
 * {@code idx_campaign_redemptions_campaign_customer}/{@code idx_campaign_redemptions_campaign_status}
 * indexes created in Feature 21.1.2; correlation-by-order-id consumers land in Feature 21.4.
 */
public interface CampaignRedemptionRepository extends JpaRepository<CampaignRedemption, UUID> {

    List<CampaignRedemption> findByCampaignIdAndCustomerId(UUID campaignId, UUID customerId);

    List<CampaignRedemption> findByCampaignIdAndStatus(UUID campaignId, RedemptionStatus status);

    Optional<CampaignRedemption> findByOrderId(UUID orderId);

    /** Per-customer redemption-cap count: CONFIRMED + still-live RESERVED rows for one customer. */
    long countByCampaignIdAndCustomerIdAndStatusIn(
            UUID campaignId, UUID customerId, Collection<RedemptionStatus> statuses);

    /** Total redemption-cap count: CONFIRMED + still-live RESERVED rows across all customers. */
    long countByCampaignIdAndStatusIn(UUID campaignId, Collection<RedemptionStatus> statuses);

    /**
     * Every {@code RESERVED} redemption whose hold has elapsed - the reservation-expiry reaper's sweep
     * target (Feature 21.4, ADR-027 Section 4 ratification amendment, mirroring
     * {@code MsisdnPoolRepository.findByStatusAndReservedUntilBefore} in subscription-service).
     */
    List<CampaignRedemption> findByStatusAndReservedUntilBefore(RedemptionStatus status, Instant instant);
}
