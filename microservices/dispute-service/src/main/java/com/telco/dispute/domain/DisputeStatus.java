package com.telco.dispute.domain;

/**
 * The Dispute lifecycle (ADR-028 Section 4).
 *
 * <pre>
 * OPENED
 *   -&gt; UNDER_REVIEW
 *        -&gt; EVIDENCE_SUBMITTED -&gt; UNDER_REVIEW   (loop: more evidence may follow more review)
 *        -&gt; RESOLVED_CUSTOMER                     (dispute uphold: credit/refund issued)
 *        -&gt; RESOLVED_MERCHANT                     (dispute rejected: invoice/payment stands)
 * OPENED | UNDER_REVIEW
 *        -&gt; WITHDRAWN                             (customer withdraws the dispute)
 * RESOLVED_CUSTOMER | RESOLVED_MERCHANT | WITHDRAWN
 *        -&gt; CLOSED                                (terminal, after settlement confirmed)
 * </pre>
 */
public enum DisputeStatus {
    OPENED,
    UNDER_REVIEW,
    EVIDENCE_SUBMITTED,
    RESOLVED_CUSTOMER,
    RESOLVED_MERCHANT,
    WITHDRAWN,
    CLOSED
}
