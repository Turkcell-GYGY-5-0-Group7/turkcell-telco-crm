package com.telco.campaign.domain.service;

import com.telco.campaign.domain.model.Campaign;
import com.telco.campaign.domain.model.CampaignRedemption;
import com.telco.campaign.domain.model.DiscountType;
import com.telco.campaign.domain.model.RedemptionStatus;
import com.telco.campaign.infrastructure.persistence.CampaignRedemptionRepository;
import com.telco.campaign.infrastructure.persistence.CampaignRepository;
import com.telco.platform.common.exception.BusinessRuleException;
import com.telco.platform.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the concurrency-safety claim in Feature 21.2.2's acceptance criteria: two simultaneous
 * reservation attempts at {@code perCustomerRedemptionCap - 1} remaining must result in exactly one
 * succeeding, never both. Exercises the real {@code PESSIMISTIC_WRITE} lock in
 * {@code CampaignRepository.findByIdForUpdate} against a real Postgres (Testcontainers, ADR-013) -
 * this cannot be proven with a mocked repository, since the whole point is serializing two real
 * concurrent transactions.
 *
 * <p>Known environment limitation (see {@code docs/tasks/lessons.md} 2026-07-12): this sandbox's
 * Docker Desktop enforces a minimum Engine API version the pinned Testcontainers client cannot
 * negotiate, so this test (like every other Testcontainers-backed test in the repo, e.g.
 * {@code CampaignRepositoryTest}, {@code starter-inbox}'s {@code InboxTransactionAtomicityTest}) may
 * not execute in this environment. It is verified by code review against the same known-good
 * pessimistic-lock pattern already proven correct in production code:
 * {@code usage-service}'s {@code QuotaRepository.findActiveForUpdateBySubscriptionId} /
 * {@code MeterCdrCommandHandler}, and {@code subscription-service}'s
 * {@code MsisdnPoolRepository.findNextFreeForUpdate} ({@code FOR UPDATE SKIP LOCKED}).
 */
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.cloud.config.enabled=false"
})
@ActiveProfiles("test")
@Testcontainers
class CampaignEligibilityServiceConcurrencyIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void configureInfrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockitoBean
    OutboxService outboxService;

    @Autowired
    CampaignEligibilityService eligibilityService;

    @Autowired
    CampaignRepository campaignRepository;

    @Autowired
    CampaignRedemptionRepository campaignRedemptionRepository;

    private Campaign campaign;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();
        // perCustomerRedemptionCap = 2, one CONFIRMED redemption already recorded -> exactly one
        // slot (cap - 1) remains. Two concurrent reservation attempts must yield exactly one winner.
        Campaign created = Campaign.create("CONCURRENCY-CAP", "Concurrency cap test", null,
                DiscountType.PERCENTAGE, new BigDecimal("5.00"), Set.of("TARIFF-A"),
                now.minus(1, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS), null, 2);
        created.activate();
        campaign = campaignRepository.save(created);

        customerId = UUID.randomUUID();
        CampaignRedemption existing = CampaignRedemption.reserve(
                campaign.getId(), customerId, UUID.randomUUID(), now.plusSeconds(3600));
        existing.confirm();
        campaignRedemptionRepository.save(existing);
    }

    @Test
    void two_concurrent_reservations_at_cap_minus_one_remaining_only_one_succeeds() throws Exception {
        int attempts = 2;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        try {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    try {
                        eligibilityService.reserve(campaign.getId(), customerId, UUID.randomUUID());
                        succeeded.incrementAndGet();
                    } catch (BusinessRuleException e) {
                        rejected.incrementAndGet();
                    }
                }));
            }

            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdown();
        }

        assertThat(succeeded.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(1);

        long liveCount = campaignRedemptionRepository.countByCampaignIdAndCustomerIdAndStatusIn(
                campaign.getId(), customerId,
                List.of(RedemptionStatus.CONFIRMED, RedemptionStatus.RESERVED));
        // 1 pre-existing CONFIRMED + exactly 1 new RESERVED, never 2 new RESERVED past the cap of 2.
        assertThat(liveCount).isEqualTo(2L);
    }
}
