package com.telco.customer.domain;

/**
 * Customer classification. INDIVIDUAL customers are identified by a TCKN; CORPORATE customers by a VKN
 * (FR-01). The MVP onboards individual customers; corporate is scaffolded for later sprints.
 */
public enum CustomerType {
    INDIVIDUAL,
    CORPORATE
}
