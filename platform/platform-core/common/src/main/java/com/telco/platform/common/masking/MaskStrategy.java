package com.telco.platform.common.masking;

/**
 * Strategy used to mask a sensitive value in the log/persistence view (ADR-021).
 *
 * <p>Masking never alters the wire view (Kafka/Avro events, HTTP responses); it applies only when a
 * value is serialized for logging or log persistence.
 */
public enum MaskStrategy {

    /** Replace the entire value with the mask character (for example {@code *****}). */
    FULL,

    /** Keep the last N characters, mask the rest (for example {@code *******8901}). Default. */
    PARTIAL,

    /** Replace the value with a stable SHA-256 hex digest, allowing correlation without exposure. */
    HASH,

    /** Local/domain-aware partial mask for email addresses (for example {@code a***@***.com}). */
    EMAIL
}
