package com.telco.catalog.infrastructure.persistence;

import com.telco.catalog.domain.model.Addon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Addon}. The {@code findByTariffs_Code} method traverses
 * the {@code tariff_addons} join table via the {@code Tariff.addons} mapping (feature 7.4.3).
 */
public interface AddonRepository extends JpaRepository<Addon, UUID> {

    Optional<Addon> findByCode(String code);

    boolean existsByCode(String code);

    /**
     * Returns all addons linked to the tariff identified by {@code tariffCode}, paginated.
     * The query navigates Addon.tariffs -> Tariff.code.
     */
    Page<Addon> findByTariffs_Code(String tariffCode, Pageable pageable);
}
