package com.telco.billing.infrastructure.persistence;

import com.telco.billing.domain.BillCycle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillCycleRepository extends JpaRepository<BillCycle, UUID> {

    Optional<BillCycle> findByCustomerId(UUID customerId);

    List<BillCycle> findByNextRunDateLessThanEqual(LocalDate today);
}
