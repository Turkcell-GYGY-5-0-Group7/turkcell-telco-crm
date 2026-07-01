package com.telco.billing.infrastructure.persistence;

import com.telco.billing.infrastructure.entity.TariffPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TariffPriceRepository extends JpaRepository<TariffPrice, UUID> {

    Optional<TariffPrice> findByTariffCode(String tariffCode);
}
