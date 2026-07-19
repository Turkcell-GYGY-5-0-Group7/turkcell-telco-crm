package com.telco.campaign.infrastructure.persistence;

import com.telco.campaign.domain.model.Campaign;
import com.telco.campaign.domain.model.CampaignStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Campaign}. Query methods needed for eligibility validation
 * (tariff-code/status/validity-window lookups) are added alongside that behavior in Feature 21.2.
 */
public interface CampaignRepository extends JpaRepository<Campaign, UUID> {

    Optional<Campaign> findByCode(String code);

    boolean existsByCode(String code);

    Optional<Campaign> findByCodeAndStatus(String code, CampaignStatus status);

    /**
     * Locks and loads a campaign for the redemption-cap read/decide/write path
     * ({@code CampaignEligibilityService.reserve}, Feature 21.2.2). The pessimistic write lock
     * serializes concurrent reservation attempts against the same campaign so the per-customer/total
     * redemption-cap counts they act on cannot race (mirrors {@code QuotaRepository
     * .findActiveForUpdateBySubscriptionId} in usage-service).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Campaign c WHERE c.id = :id")
    Optional<Campaign> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Resolves candidate campaigns for the {@code campaignCode}-omitted validate path (Feature
     * 21.3.1): every ACTIVE campaign whose {@code applicableTariffCodes} contains the given tariff
     * code, ordered by {@code discountValue} descending. Tie-break rule (documented in
     * {@code docs/api-contracts/campaign-service.md}): when more than one ACTIVE campaign matches the
     * tariff, the caller picks the first result (highest raw {@code discountValue}, regardless of
     * {@code discountType} - a simple, deterministic rule, not a currency-normalized comparison).
     * Validity-window and redemption-cap checks are re-applied afterward by
     * {@code CampaignEligibilityService.evaluate(...)} against the chosen candidate's code.
     */
    @Query("SELECT c FROM Campaign c JOIN c.applicableTariffCodes t "
            + "WHERE c.status = :status AND t = :tariffCode "
            + "ORDER BY c.discountValue DESC")
    List<Campaign> findByStatusAndApplicableTariffCode(@Param("status") CampaignStatus status,
                                                         @Param("tariffCode") String tariffCode);
}
