package com.telco.dispute.infrastructure.storage;

import java.time.Duration;

/**
 * Port for dispute evidence binary storage (ADR-006). The default adapter is MinIO/S3; the binary is
 * never stored in {@code dispute-db}. Locally-built, flagged for a future platform
 * {@code starter-storage} (mirrors customer-service's {@code DocumentStorage} pattern).
 */
public interface DisputeEvidenceStorage {

    /**
     * Stores an object under {@code objectKey} and returns the stored key (the {@code object_ref}
     * persisted on the {@code DisputeEvidence} row).
     */
    String store(String objectKey, byte[] content, String contentType);

    /** Returns a time-limited pre-signed GET URL for downloading the object. */
    String presignedGetUrl(String objectKey, Duration ttl);
}
