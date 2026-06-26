package com.telco.catalog.domain.service;

import com.telco.catalog.domain.model.Tariff;
import com.telco.catalog.domain.model.TariffVersion;
import com.telco.catalog.infrastructure.persistence.TariffVersionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Domain service responsible for tariff versioning (FR-CAT-03).
 *
 * <p>When a price or attribute changes on a tariff, this service:
 * <ol>
 *   <li>Applies the change to the {@link Tariff} aggregate (which bumps its {@code version} field).</li>
 *   <li>Persists an immutable {@link TariffVersion} snapshot of the new state.</li>
 * </ol>
 *
 * <p>Prior version snapshots remain unchanged. {@link #resolveActiveVersion} answers point-in-time
 * queries for downstream consumers such as order-service.
 */
@Service
public class TariffVersioningService {

    private final TariffVersionRepository versionRepository;

    public TariffVersioningService(TariffVersionRepository versionRepository) {
        this.versionRepository = versionRepository;
    }

    /**
     * Applies a price change to {@code tariff}, bumps its version, and persists an immutable
     * snapshot. Must be called inside an active transaction (mediator TransactionBehavior ensures
     * this when dispatched via the mediator).
     *
     * @param tariff        the tariff aggregate (already loaded by the caller)
     * @param newMonthlyFee the new price — must be non-negative
     */
    public void applyPriceChange(Tariff tariff, BigDecimal newMonthlyFee) {
        tariff.applyPriceChange(newMonthlyFee);
        TariffVersion snapshot = TariffVersion.snapshot(tariff);
        versionRepository.save(snapshot);
    }

    /**
     * Creates the initial version snapshot for a newly created tariff. Should be called once,
     * immediately after the tariff is first persisted.
     *
     * @param tariff the newly persisted tariff
     */
    public void createInitialSnapshot(Tariff tariff) {
        TariffVersion snapshot = TariffVersion.snapshot(tariff);
        versionRepository.save(snapshot);
    }

    /**
     * Returns the tariff version that was effective at {@code asOf}. Selects the version with the
     * highest version number whose {@code effectiveFrom} is at or before {@code asOf}.
     *
     * @param tariffCode the tariff code
     * @param asOf       the point in time to resolve
     * @return the matching snapshot, or empty if none exists
     */
    public Optional<TariffVersion> resolveActiveVersion(String tariffCode, Instant asOf) {
        return versionRepository
                .findFirstByTariffCodeAndEffectiveFromBeforeOrderByVersionDesc(tariffCode, asOf);
    }
}
