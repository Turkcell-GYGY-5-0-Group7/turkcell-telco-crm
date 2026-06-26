package com.telco.customer.infrastructure.storage;

import java.time.Duration;

/**
 * Port for KYC document binary storage (ADR-006). The default adapter is MinIO/S3; the binary is never
 * stored in the database. Locally-built, flagged for a future platform {@code starter-storage}.
 */
public interface DocumentStorage {

    /**
     * Stores an object under {@code objectKey} and returns the stored key (the {@code file_ref}
     * persisted on the document row).
     */
    String store(String objectKey, byte[] content, String contentType);

    /** Returns a time-limited pre-signed GET URL for downloading the object. */
    String presignedGetUrl(String objectKey, Duration ttl);
}
