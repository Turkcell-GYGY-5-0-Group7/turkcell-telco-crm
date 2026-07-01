package com.telco.subscription.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * SIM-card inventory record (FR-15). {@code iccid} is the physical card id; {@code imsi} and
 * {@code msisdn} link it to network identity and the assigned number once activated.
 *
 * <p>SIM assignment behavior is delivered with the activation flow in feature 9.3; this aggregate
 * maps the existing {@code sim_cards} table for the domain to operate on.
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

    /** For JPA only. */
    protected SimCard() {
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
