package com.telco.subscription.domain;

import com.telco.platform.common.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A single MSISDN row in the number pool inventory (FR-13).
 *
 * <p>The pool state machine is enforced here: FREE -> RESERVED -> ALLOCATED, with a RESERVED hold
 * bounded by {@code reservedUntil}; an ALLOCATED or RESERVED number returns to FREE on
 * {@link #release()}. Illegal transitions raise {@link BusinessRuleException}.
 */
@Entity
@Table(name = "msisdn_pool")
public class MsisdnPool {

    @Id
    @Column(name = "msisdn", length = 20)
    private String msisdn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private MsisdnStatus status;

    @Column(name = "reserved_until")
    private Instant reservedUntil;

    /** For JPA only. */
    protected MsisdnPool() {
    }

    /**
     * FREE -> RESERVED, holding the number until {@code reservedUntil}. Reserving a number that is not
     * FREE raises {@link BusinessRuleException}.
     */
    public void reserve(Instant reservedUntil) {
        if (this.status != MsisdnStatus.FREE) {
            throw new BusinessRuleException(
                    "Cannot reserve MSISDN in status: " + this.status.name()
                            + ". Only FREE numbers may be reserved.");
        }
        this.status = MsisdnStatus.RESERVED;
        this.reservedUntil = reservedUntil;
    }

    /**
     * RESERVED -> ALLOCATED, committing the hold. Allocating a number that is not RESERVED raises
     * {@link BusinessRuleException}.
     */
    public void allocate() {
        if (this.status != MsisdnStatus.RESERVED) {
            throw new BusinessRuleException(
                    "Cannot allocate MSISDN in status: " + this.status.name()
                            + ". Only RESERVED numbers may be allocated.");
        }
        this.status = MsisdnStatus.ALLOCATED;
        this.reservedUntil = null;
    }

    /**
     * ALLOCATED or RESERVED -> FREE, returning the number to the pool. Releasing a number that is
     * already FREE raises {@link BusinessRuleException}.
     */
    public void release() {
        if (this.status == MsisdnStatus.FREE) {
            throw new BusinessRuleException(
                    "Cannot release MSISDN in status: " + this.status.name()
                            + ". Number is already FREE.");
        }
        this.status = MsisdnStatus.FREE;
        this.reservedUntil = null;
    }

    public String getMsisdn() {
        return msisdn;
    }

    public MsisdnStatus getStatus() {
        return status;
    }

    public Instant getReservedUntil() {
        return reservedUntil;
    }
}
