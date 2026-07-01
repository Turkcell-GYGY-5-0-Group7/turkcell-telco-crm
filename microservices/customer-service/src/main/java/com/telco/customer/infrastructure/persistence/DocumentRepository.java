package com.telco.customer.infrastructure.persistence;

import com.telco.customer.domain.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Persistence for customer KYC {@link Document} references (FR-03). */
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByCustomerId(UUID customerId);
}
