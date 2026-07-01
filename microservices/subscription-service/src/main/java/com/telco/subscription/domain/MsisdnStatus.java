package com.telco.subscription.domain;

/** Inventory states of an MSISDN in the number pool: FREE -> RESERVED -> ALLOCATED, release -> FREE. FR-13. */
public enum MsisdnStatus {
    FREE,
    RESERVED,
    ALLOCATED
}
