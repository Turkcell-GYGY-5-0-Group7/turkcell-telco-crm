package com.telco.campaign.domain.service;

import com.telco.campaign.domain.event.CampaignExpiredEvent;
import com.telco.campaign.domain.model.Campaign;
import com.telco.campaign.domain.model.CampaignRedemption;
import com.telco.campaign.domain.model.CampaignStatus;
import com.telco.campaign.domain.model.EligibilityDecision;
import com.telco.campaign.domain.model.EligibilityReason;
import com.telco.campaign.domain.model.RedemptionStatus;
import com.telco.campaign.infrastructure.persistence.CampaignRedemptionRepository;
import com.telco.campaign.infrastructure.persistence.CampaignRepository;
import com.telco.platform.common.exception.BusinessRuleException;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.outbox.OutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Domain service evaluating campaign eligibility and enforcing redemption caps
 * (design-note.md Section 5, ADR-027 Decision Section 4, Features 21.2.2/21.2.3).
 *
 * <p>Not invoked through the {@code Mediator} (this is a plain domain service, not a command/query
 * handler) - 21.3's validate endpoint and 21.4's event consumers call it directly. Both public methods
 * are self-transactional ({@code @Transactional}) rather than relying on the mediator's
 * {@code TransactionBehavior}, since neither caller guarantees a surrounding mediator-dispatched
 * transaction.
 *
 * <p>{@link #evaluate} is deliberately a pure read except for the defensive auto-expire side effect
 * (ADR-027: "Redemption is not counted at the synchronous validation call ... it is a pure read").
 * {@link #reserve} is the concurrency-safe write path that actually consumes a cap slot; it locks the
 * {@link Campaign} row ({@code PESSIMISTIC_WRITE}) for the duration of the count-then-write decision so
 * two concurrent reservation attempts against the same campaign cannot both observe a slot as free.
 */
@Service
public class CampaignEligibilityService {

    /** CONFIRMED + still-live RESERVED rows both count against a cap (design-note.md Section 5). */
    private static final Set<RedemptionStatus> LIVE_STATUSES =
            EnumSet.of(RedemptionStatus.CONFIRMED, RedemptionStatus.RESERVED);

    /**
     * Default hold duration for a new reservation before the Feature 21.4 reservation-expiry reaper
     * may reclaim it. Not mandated by ADR-027; a reasonable placeholder subject to tuning once 21.4
     * wires the reaper and the real order-to-payment latency is observed in practice.
     */
    private static final Duration DEFAULT_RESERVATION_HOLD = Duration.ofHours(24);

    private static final String OUTBOX_AGGREGATE_TYPE = "campaign";
    private static final String EXPIRED_EVENT_TYPE = "campaign.expired.v1";

    private final CampaignRepository campaignRepository;
    private final CampaignRedemptionRepository campaignRedemptionRepository;
    private final OutboxService outboxService;

    public CampaignEligibilityService(CampaignRepository campaignRepository,
                                       CampaignRedemptionRepository campaignRedemptionRepository,
                                       OutboxService outboxService) {
        this.campaignRepository = campaignRepository;
        this.campaignRedemptionRepository = campaignRedemptionRepository;
        this.outboxService = outboxService;
    }

    /**
     * Evaluates whether {@code customerId} is eligible for {@code campaignCode} against
     * {@code tariffCode}: validity window (validFrom &lt;= now &lt;= validTo, status == ACTIVE),
     * tariff-code applicability, then the per-customer/total redemption caps (21.2.2). Also
     * defensively auto-expires the campaign if it is still ACTIVE but its {@code validTo} has already
     * passed.
     *
     * @param campaignCode the admin-assigned campaign code (unique, {@code Campaign.code})
     * @param customerId   the customer attempting to redeem
     * @param tariffCode   the tariff code the order is being priced against
     */
    @Transactional
    public EligibilityDecision evaluate(String campaignCode, UUID customerId, String tariffCode) {
        Optional<Campaign> campaignOpt = campaignRepository.findByCode(campaignCode);
        if (campaignOpt.isEmpty()) {
            return EligibilityDecision.ineligible(EligibilityReason.CAMPAIGN_NOT_FOUND);
        }
        Campaign campaign = campaignOpt.get();
        Instant now = Instant.now();

        // Defensive auto-expire: a lapsed campaign must not linger ACTIVE (21.2.3). Publishes
        // campaign.expired.v1 through the outbox atomically with the status update (Feature 21.4.1) -
        // this is the only real call site of Campaign.expire() today (no dedicated admin command), so
        // the outbox publish lives here rather than in a command handler.
        if (campaign.getStatus() == CampaignStatus.ACTIVE && now.isAfter(campaign.getValidTo())) {
            campaign.expire();
            campaignRepository.save(campaign);
            outboxService.publish(
                    OUTBOX_AGGREGATE_TYPE,
                    campaign.getId().toString(),
                    EXPIRED_EVENT_TYPE,
                    new CampaignExpiredEvent(
                            campaign.getId().toString(),
                            campaign.getCode(),
                            now.toString()
                    )
            );
        }

        if (campaign.getStatus() == CampaignStatus.EXPIRED) {
            return EligibilityDecision.ineligible(campaign.getId(), EligibilityReason.EXPIRED);
        }
        if (campaign.getStatus() != CampaignStatus.ACTIVE) {
            return EligibilityDecision.ineligible(campaign.getId(), EligibilityReason.NOT_ACTIVE_STATUS);
        }
        if (now.isBefore(campaign.getValidFrom())) {
            return EligibilityDecision.ineligible(campaign.getId(), EligibilityReason.NOT_YET_ACTIVE);
        }
        if (now.isAfter(campaign.getValidTo())) {
            // Defensive fallback; the auto-expire branch above should already have caught this.
            return EligibilityDecision.ineligible(campaign.getId(), EligibilityReason.EXPIRED);
        }
        if (!campaign.getApplicableTariffCodes().contains(tariffCode)) {
            return EligibilityDecision.ineligible(campaign.getId(), EligibilityReason.TARIFF_NOT_APPLICABLE);
        }

        Optional<EligibilityReason> capReason = capExceededReason(campaign, customerId);
        if (capReason.isPresent()) {
            return EligibilityDecision.ineligible(campaign.getId(), capReason.get());
        }

        return EligibilityDecision.eligible(campaign.getId(), campaign.getDiscountType(),
                campaign.getDiscountValue());
    }

    /**
     * Reserves a redemption slot for {@code (campaignId, customerId)}, enforcing the per-customer and
     * total redemption caps atomically under a pessimistic lock on the {@link Campaign} row so
     * concurrent attempts against the same campaign cannot both succeed past the cap
     * (design-note.md Section 5, Feature 21.2.2 concurrency requirement). Callable now for the
     * cap-safety proof (21.2.2's acceptance criteria); wired to the {@code order.created.v1} consumer
     * in Feature 21.4.
     *
     * @throws ResourceNotFoundException if no campaign exists for {@code campaignId}
     * @throws BusinessRuleException     if either cap is already exhausted
     */
    @Transactional
    public CampaignRedemption reserve(UUID campaignId, UUID customerId, UUID orderId) {
        Campaign campaign = campaignRepository.findByIdForUpdate(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Campaign not found with id: " + campaignId));

        Optional<EligibilityReason> capReason = capExceededReason(campaign, customerId);
        if (capReason.isPresent()) {
            throw new BusinessRuleException(
                    "Cannot reserve redemption for campaign " + campaign.getCode()
                            + ": " + capReason.get());
        }

        CampaignRedemption redemption = CampaignRedemption.reserve(
                campaignId, customerId, orderId, Instant.now().plus(DEFAULT_RESERVATION_HOLD));
        return campaignRedemptionRepository.save(redemption);
    }

    /**
     * Checks the per-customer cap first, then the total cap (skipped entirely when
     * {@code totalRedemptionCap} is {@code null} - unlimited), returning the first exceeded reason, if
     * any.
     */
    private Optional<EligibilityReason> capExceededReason(Campaign campaign, UUID customerId) {
        long perCustomerCount = campaignRedemptionRepository.countByCampaignIdAndCustomerIdAndStatusIn(
                campaign.getId(), customerId, LIVE_STATUSES);
        if (perCustomerCount >= campaign.getPerCustomerRedemptionCap()) {
            return Optional.of(EligibilityReason.PER_CUSTOMER_CAP_EXCEEDED);
        }

        Integer totalCap = campaign.getTotalRedemptionCap();
        if (totalCap != null) {
            long totalCount = campaignRedemptionRepository.countByCampaignIdAndStatusIn(
                    campaign.getId(), LIVE_STATUSES);
            if (totalCount >= totalCap) {
                return Optional.of(EligibilityReason.TOTAL_CAP_EXCEEDED);
            }
        }

        return Optional.empty();
    }
}
