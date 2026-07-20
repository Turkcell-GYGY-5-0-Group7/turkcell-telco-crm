package com.telco.dispute.infrastructure.persistence;

import com.telco.dispute.domain.Dispute;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DisputeRepository extends JpaRepository<Dispute, UUID> {

    Page<Dispute> findByCustomerId(UUID customerId, Pageable pageable);
}
