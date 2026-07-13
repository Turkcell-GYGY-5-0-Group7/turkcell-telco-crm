package com.telco.subscription.application.handler;

import com.telco.platform.cqrs.CommandHandler;
import com.telco.subscription.application.AuditLogWriter;
import com.telco.subscription.application.command.ExpireMsisdnReservationsCommand;
import com.telco.subscription.application.command.ExpireMsisdnReservationsResult;
import com.telco.subscription.domain.MsisdnPool;
import com.telco.subscription.domain.MsisdnStatus;
import com.telco.subscription.infrastructure.MsisdnPoolRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Releases every expired {@code RESERVED} MSISDN hold back to {@code FREE} (feature 17.3, FR-13),
 * driven through {@link MsisdnPool#release()} - never a direct SQL update bypassing the domain
 * method's state-machine guard. Writes one {@code audit_log} row per release (ADR-021, NFR-12).
 *
 * <p>Runs inside the mediator {@code TransactionBehavior}'s transaction: each release and its audit
 * row commit atomically per subscription-service's mandatory audit-logging rule.
 */
@Component
public class ExpireMsisdnReservationsCommandHandler
        implements CommandHandler<ExpireMsisdnReservationsCommand, ExpireMsisdnReservationsResult> {

    private static final String AGGREGATE_TYPE = "MsisdnPool";
    private static final String ACTION_RESERVATION_EXPIRED = "MSISDN_RESERVATION_EXPIRED";

    private final MsisdnPoolRepository msisdnPoolRepository;
    private final AuditLogWriter auditLogWriter;

    public ExpireMsisdnReservationsCommandHandler(
            MsisdnPoolRepository msisdnPoolRepository, AuditLogWriter auditLogWriter) {
        this.msisdnPoolRepository = msisdnPoolRepository;
        this.auditLogWriter = auditLogWriter;
    }

    @Override
    public ExpireMsisdnReservationsResult handle(ExpireMsisdnReservationsCommand command) {
        List<MsisdnPool> expired =
                msisdnPoolRepository.findByStatusAndReservedUntilBefore(MsisdnStatus.RESERVED, Instant.now());

        for (MsisdnPool pool : expired) {
            String msisdn = pool.getMsisdn();
            Instant previousReservedUntil = pool.getReservedUntil();

            pool.release();
            msisdnPoolRepository.save(pool);

            auditLogWriter.log(
                    ACTION_RESERVATION_EXPIRED,
                    AGGREGATE_TYPE,
                    msisdn,
                    Map.of("previousReservedUntil", String.valueOf(previousReservedUntil)));
        }

        return new ExpireMsisdnReservationsResult(expired.size());
    }
}
