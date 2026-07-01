package com.telco.ticket.infrastructure.persistence;

import com.telco.ticket.domain.SlaPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, UUID> {
    Optional<SlaPolicy> findByCategoryAndPriority(String category, String priority);
}
