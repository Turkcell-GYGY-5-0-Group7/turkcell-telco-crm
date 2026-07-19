package com.telco.dispute.infrastructure.persistence;

import com.telco.dispute.domain.DisputeStateHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DisputeStateHistoryRepository extends JpaRepository<DisputeStateHistory, UUID> {
}
