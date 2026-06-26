package com.telco.catalog.infrastructure.persistence;

import com.telco.catalog.domain.model.TariffVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link TariffVersion} snapshots (feature 7.2.3).
 * All query methods are read-only; snapshots are never mutated after creation.
 */
public interface TariffVersionRepository extends JpaRepository<TariffVersion, UUID> {

    List<TariffVersion> findByTariffCodeOrderByVersionDesc(String tariffCode);

    Optional<TariffVersion> findByTariffCodeAndVersion(String tariffCode, int version);

    /**
     * Finds the most recent version of the tariff that was effective at or before {@code asOf},
     * ordered by version descending so the highest matching version is first.
     */
    Optional<TariffVersion> findFirstByTariffCodeAndEffectiveFromBeforeOrderByVersionDesc(
            String tariffCode, Instant asOf);
}
