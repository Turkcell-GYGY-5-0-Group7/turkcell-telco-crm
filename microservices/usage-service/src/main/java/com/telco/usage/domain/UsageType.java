package com.telco.usage.domain;

/** Discriminator for usage records and quota buckets. */
public enum UsageType {
    VOICE,
    SMS,
    DATA
}
