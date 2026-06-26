package com.telco.catalog.infrastructure.persistence;

import com.telco.catalog.domain.model.Tariff;
import com.telco.catalog.domain.model.TariffStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Tariff}. Provides window-based active tariff queries
 * for the catalog read API (feature 7.4.3).
 */
public interface TariffRepository extends JpaRepository<Tariff, UUID> {

    Optional<Tariff> findByCode(String code);

    boolean existsByCode(String code);

    Page<Tariff> findByStatus(TariffStatus status, Pageable pageable);

    /**
     * Returns tariffs in the given status whose effective window contains {@code now}: the tariff
     * has already started ({@code effectiveFrom <= now}) and has not yet ended
     * ({@code effectiveTo} is null OR {@code effectiveTo > now}).
     */
    Page<Tariff> findByStatusAndEffectiveFromBeforeAndEffectiveToIsNull(
            TariffStatus status, Instant effectiveFrom, Pageable pageable);

    Page<Tariff> findByStatusAndEffectiveFromBeforeAndEffectiveToAfter(
            TariffStatus status, Instant effectiveFrom, Instant effectiveTo, Pageable pageable);
}
