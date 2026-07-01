package com.telco.customer.infrastructure.persistence;

import com.telco.customer.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Persistence for {@link AuditLog} records (NFR-12). */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
}
