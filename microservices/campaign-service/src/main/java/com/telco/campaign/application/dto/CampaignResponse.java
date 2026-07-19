package com.telco.campaign.application.dto;

import com.telco.campaign.domain.model.Campaign;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Read DTO for a campaign. Domain entities are never exposed directly (ADR-015). */
public record CampaignResponse(
        UUID id,
        String code,
        String name,
        String description,
        String discountType,
        BigDecimal discountValue,
        Set<String> applicableTariffCodes,
        Instant validFrom,
        Instant validTo,
        String status,
        Integer totalRedemptionCap,
        int perCustomerRedemptionCap,
        Instant createdAt,
        Instant updatedAt,
        int version,
        boolean staleTariffFlag,
        String staleTariffReason,
        Instant staleTariffFlaggedAt
) {

    /**
     * Maps a {@link Campaign} to its response DTO. {@code applicableTariffCodes} is eagerly copied
     * into a plain {@link LinkedHashSet} here (not passed through as the lazy-backed unmodifiable
     * view {@code Campaign.getApplicableTariffCodes()} returns) - this call must run inside the live
     * Hibernate session (the handler's method body), because Jackson serialization of the HTTP
     * response happens later, outside that session/transaction boundary. Mirrors the
     * {@code LazyInitializationException} fix pattern documented in {@code docs/tasks/lessons.md}
     * (2026-07-06 entry).
     */
    public static CampaignResponse from(Campaign campaign) {
        return new CampaignResponse(
                campaign.getId(),
                campaign.getCode(),
                campaign.getName(),
                campaign.getDescription(),
                campaign.getDiscountType().name(),
                campaign.getDiscountValue(),
                new LinkedHashSet<>(campaign.getApplicableTariffCodes()),
                campaign.getValidFrom(),
                campaign.getValidTo(),
                campaign.getStatus().name(),
                campaign.getTotalRedemptionCap(),
                campaign.getPerCustomerRedemptionCap(),
                campaign.getCreatedAt(),
                campaign.getUpdatedAt(),
                campaign.getVersion(),
                campaign.isStaleTariffFlag(),
                campaign.getStaleTariffReason(),
                campaign.getStaleTariffFlaggedAt()
        );
    }
}
