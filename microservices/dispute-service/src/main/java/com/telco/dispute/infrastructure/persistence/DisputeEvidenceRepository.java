package com.telco.dispute.infrastructure.persistence;

import com.telco.dispute.domain.DisputeEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DisputeEvidenceRepository extends JpaRepository<DisputeEvidence, UUID> {
}
