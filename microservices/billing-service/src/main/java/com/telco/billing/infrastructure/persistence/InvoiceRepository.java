package com.telco.billing.infrastructure.persistence;

import com.telco.billing.domain.Invoice;
import com.telco.billing.domain.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Page<Invoice> findByCustomerId(UUID customerId, Pageable pageable);

    boolean existsBySubscriptionIdAndPeriodStart(UUID subscriptionId, Instant periodStart);

    Optional<Invoice> findBySubscriptionIdAndPeriodStart(UUID subscriptionId, Instant periodStart);

    @Query("SELECT i FROM Invoice i LEFT JOIN FETCH i.lines WHERE i.subscriptionId = :subscriptionId AND i.periodStart = :periodStart")
    Optional<Invoice> findBySubscriptionIdAndPeriodStartWithLines(
            @Param("subscriptionId") UUID subscriptionId,
            @Param("periodStart") Instant periodStart);

    // ON_HOLD invoices are excluded from dunning while a dispute is under review (ADR-028 Section 5) -
    // the hold suppresses collections without excusing eventual payment once the dispute resolves.
    @Query("SELECT i FROM Invoice i WHERE i.status = :status AND i.dueDate < :today "
            + "AND i.disputeStatus <> com.telco.billing.domain.InvoiceDisputeStatus.ON_HOLD")
    List<Invoice> findOverdue(@Param("status") InvoiceStatus status, @Param("today") LocalDate today);
}
