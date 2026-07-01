package com.telco.identity.infrastructure.persistence;

import com.telco.identity.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Spring Data JPA repository for {@link AuditLog}. */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
}
