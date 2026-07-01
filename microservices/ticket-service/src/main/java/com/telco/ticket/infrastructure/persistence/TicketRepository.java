package com.telco.ticket.infrastructure.persistence;

import com.telco.ticket.domain.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    @Query("SELECT t FROM Ticket t LEFT JOIN FETCH t.comments WHERE t.id = :id")
    Optional<Ticket> findByIdWithComments(@Param("id") UUID id);

    @Query("SELECT t FROM Ticket t WHERE t.status IN ('OPEN','ASSIGNED') "
            + "AND t.slaDueAt < :now AND t.slaBreachedAt IS NULL")
    List<Ticket> findBreached(@Param("now") Instant now);
}
