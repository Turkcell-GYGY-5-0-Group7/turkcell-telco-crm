package com.telco.campaign.infrastructure.persistence;

import com.telco.campaign.domain.model.Campaign;
import com.telco.campaign.domain.model.CampaignRedemption;
import com.telco.campaign.domain.model.CampaignStatus;
import com.telco.campaign.domain.model.DiscountType;
import com.telco.campaign.domain.model.RedemptionStatus;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CampaignRepositoryTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    static {
        POSTGRES.start();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/migration/platform")
                .load()
                .migrate();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.cloud.config.enabled", () -> "false");
        registry.add("eureka.client.enabled", () -> "false");
    }

    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CampaignRedemptionRepository campaignRedemptionRepository;
    @Autowired private TestEntityManager entityManager;

    @Test
    void findByCode_returns_campaign_and_existsByCode_matches() {
        Campaign campaign = saveCampaign("WELCOME10", CampaignStatus.DRAFT);
        flushAndClear();

        Campaign found = campaignRepository.findByCode("WELCOME10").orElseThrow();
        assertThat(found.getId()).isEqualTo(campaign.getId());
        assertThat(found.getName()).isEqualTo("WELCOME10 campaign");
        assertThat(found.getDescription()).isEqualTo("10% off for new customers");
        assertThat(found.getDiscountType()).isEqualTo(DiscountType.PERCENTAGE);
        assertThat(found.getDiscountValue()).isEqualByComparingTo("10.00");
        assertThat(found.getApplicableTariffCodes()).containsExactlyInAnyOrder("TARIFF-A", "TARIFF-B");
        assertThat(found.getStatus()).isEqualTo(CampaignStatus.DRAFT);
        assertThat(found.getTotalRedemptionCap()).isEqualTo(1000);
        assertThat(found.getPerCustomerRedemptionCap()).isEqualTo(1);
        assertThat(found.getValidFrom()).isNotNull();
        assertThat(found.getValidTo()).isNotNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
        // Hibernate's element-collection persister issues a version-incrementing UPDATE when it
        // recreates a non-empty @ElementCollection (applicableTariffCodes), even on the initial
        // persist - so a freshly created campaign is at version 1, not 0, by the time it is re-read.
        assertThat(found.getVersion()).isEqualTo(1);

        assertThat(campaignRepository.existsByCode("WELCOME10")).isTrue();
        assertThat(campaignRepository.existsByCode("MISSING")).isFalse();
    }

    @Test
    void findByCodeAndStatus_only_matches_the_given_status() {
        saveCampaign("ACTIVE-CODE", CampaignStatus.ACTIVE);
        flushAndClear();

        assertThat(campaignRepository.findByCodeAndStatus("ACTIVE-CODE", CampaignStatus.ACTIVE))
                .isPresent();
        assertThat(campaignRepository.findByCodeAndStatus("ACTIVE-CODE", CampaignStatus.PAUSED))
                .isEmpty();
    }

    @Test
    void campaignRedemptionRepository_supports_cap_and_correlation_lookups() {
        Campaign campaign = saveCampaign("REDEEM-CODE", CampaignStatus.ACTIVE);
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        CampaignRedemption redemption = new CampaignRedemption(
                UUID.randomUUID(), campaign.getId(), customerId, orderId,
                RedemptionStatus.RESERVED, Instant.parse("2026-07-13T00:00:00Z"), null,
                Instant.parse("2026-07-14T00:00:00Z"));
        campaignRedemptionRepository.save(redemption);
        flushAndClear();

        assertThat(redemption.getId()).isNotNull();
        assertThat(redemption.getCampaignId()).isEqualTo(campaign.getId());
        assertThat(redemption.getCustomerId()).isEqualTo(customerId);
        assertThat(redemption.getOrderId()).isEqualTo(orderId);
        assertThat(redemption.getStatus()).isEqualTo(RedemptionStatus.RESERVED);
        assertThat(redemption.getRedeemedAt()).isNotNull();
        assertThat(redemption.getConfirmedAt()).isNull();
        assertThat(redemption.getReservedUntil()).isEqualTo(Instant.parse("2026-07-14T00:00:00Z"));

        List<CampaignRedemption> byCustomer = campaignRedemptionRepository
                .findByCampaignIdAndCustomerId(campaign.getId(), customerId);
        assertThat(byCustomer).hasSize(1);

        List<CampaignRedemption> byStatus = campaignRedemptionRepository
                .findByCampaignIdAndStatus(campaign.getId(), RedemptionStatus.RESERVED);
        assertThat(byStatus).hasSize(1);

        assertThat(campaignRedemptionRepository.findByOrderId(orderId)).isPresent();
        assertThat(campaignRedemptionRepository.findByOrderId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void countByCampaignIdAndCustomerIdAndStatusIn_counts_confirmed_and_reserved_but_not_released() {
        Campaign campaign = saveCampaign("CAP-CODE", CampaignStatus.ACTIVE);
        UUID customerId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-13T00:00:00Z");

        campaignRedemptionRepository.save(new CampaignRedemption(
                UUID.randomUUID(), campaign.getId(), customerId, UUID.randomUUID(),
                RedemptionStatus.RESERVED, now, null, now.plusSeconds(3600)));
        campaignRedemptionRepository.save(new CampaignRedemption(
                UUID.randomUUID(), campaign.getId(), customerId, UUID.randomUUID(),
                RedemptionStatus.CONFIRMED, now, now, null));
        campaignRedemptionRepository.save(new CampaignRedemption(
                UUID.randomUUID(), campaign.getId(), customerId, UUID.randomUUID(),
                RedemptionStatus.RELEASED, now, null, null));
        flushAndClear();

        long liveCount = campaignRedemptionRepository.countByCampaignIdAndCustomerIdAndStatusIn(
                campaign.getId(), customerId,
                List.of(RedemptionStatus.CONFIRMED, RedemptionStatus.RESERVED));
        long totalCount = campaignRedemptionRepository.countByCampaignIdAndStatusIn(
                campaign.getId(), List.of(RedemptionStatus.CONFIRMED, RedemptionStatus.RESERVED));

        assertThat(liveCount).isEqualTo(2L);
        assertThat(totalCount).isEqualTo(2L);
    }

    @Test
    void findByIdForUpdate_returns_the_locked_campaign() {
        Campaign campaign = saveCampaign("LOCK-CODE", CampaignStatus.ACTIVE);
        flushAndClear();

        Campaign locked = campaignRepository.findByIdForUpdate(campaign.getId()).orElseThrow();

        assertThat(locked.getId()).isEqualTo(campaign.getId());
    }

    private Campaign saveCampaign(String code, CampaignStatus status) {
        Instant now = Instant.parse("2026-07-13T00:00:00Z");
        Campaign campaign = new Campaign(
                UUID.randomUUID(), code, code + " campaign", "10% off for new customers",
                DiscountType.PERCENTAGE, new BigDecimal("10.00"),
                Set.of("TARIFF-A", "TARIFF-B"),
                now, now.plusSeconds(86_400L * 30), status,
                1000, 1, now, now);
        return campaignRepository.save(campaign);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
