package com.telco.subscription.domain;

import com.telco.platform.common.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * SIM-card inventory aggregate (FR-15). {@code iccid} is the physical card id; {@code imsi} links
 * it to network identity; {@code msisdn} is populated on assignment and cleared on release.
 *
 * <p>State machine: AVAILABLE -&gt; ASSIGNED -&gt; AVAILABLE (on release) / SUSPENDED (on sub
 * suspend) -&gt; ASSIGNED (on restore) -&gt; DECOMMISSIONED (terminal, any state).
 */
@Entity
@Table(name = "sim_cards")
public class SimCard {

    @Id
    @Column(name = "iccid", length = 22)
    private String iccid;

    @Column(name = "imsi", nullable = false, length = 15)
    private String imsi;

    @Column(name = "msisdn", length = 20)
    private String msisdn;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    protected SimCard() {
    }

    /** Assigns this SIM to the given {@code msisdn}. Only valid from AVAILABLE. */
    public void assign(String msisdn) {
        if (!SimCardStatus.AVAILABLE.name().equals(status)) {
            throw new BusinessRuleException("SimCard " + iccid + " cannot be assigned from status " + status);
        }
        this.msisdn = msisdn;
        this.status = SimCardStatus.ASSIGNED.name();
    }

    /** Releases the SIM back to AVAILABLE. Only valid from ASSIGNED. */
    public void release() {
        if (!SimCardStatus.ASSIGNED.name().equals(status)) {
            throw new BusinessRuleException("SimCard " + iccid + " cannot be released from status " + status);
        }
        this.msisdn = null;
        this.status = SimCardStatus.AVAILABLE.name();
    }

    /** Suspends the SIM. Only valid from ASSIGNED. */
    public void suspend() {
        if (!SimCardStatus.ASSIGNED.name().equals(status)) {
            throw new BusinessRuleException("SimCard " + iccid + " cannot be suspended from status " + status);
        }
        this.status = SimCardStatus.SUSPENDED.name();
    }

    /** Restores a suspended SIM to ASSIGNED. Only valid from SUSPENDED. */
    public void restore() {
        if (!SimCardStatus.SUSPENDED.name().equals(status)) {
            throw new BusinessRuleException("SimCard " + iccid + " cannot be restored from status " + status);
        }
        this.status = SimCardStatus.ASSIGNED.name();
    }

    /** Permanently retires this SIM. Valid from any non-DECOMMISSIONED state. */
    public void decommission() {
        if (SimCardStatus.DECOMMISSIONED.name().equals(status)) {
            throw new BusinessRuleException("SimCard " + iccid + " is already decommissioned");
        }
        this.msisdn = null;
        this.status = SimCardStatus.DECOMMISSIONED.name();
    }

    public String getIccid() {
        return iccid;
    }

    public String getImsi() {
        return imsi;
    }

    public String getMsisdn() {
        return msisdn;
    }

    public String getStatus() {
        return status;
    }
}
