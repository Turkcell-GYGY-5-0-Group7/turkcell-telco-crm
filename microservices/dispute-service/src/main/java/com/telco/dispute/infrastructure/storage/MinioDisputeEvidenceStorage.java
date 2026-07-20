package com.telco.dispute.infrastructure.storage;

import com.telco.platform.common.exception.DependencyFailureException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.time.Duration;

/**
 * MinIO/S3 adapter for {@link DisputeEvidenceStorage} (ADR-006), mirroring customer-service's
 * {@code MinioDocumentStorage} exactly.
 *
 * <p>All outbound MinIO calls are guarded by the shared {@code minio} circuit breaker
 * (`microservices/configs/application.yml`). {@code store} also uses the {@code minio} retry policy;
 * {@code presignedGetUrl} fails fast since URL signing is local and a retry adds no benefit.
 */
@Component
class MinioDisputeEvidenceStorage implements DisputeEvidenceStorage {

    private final MinioClient client;
    private final String bucket;

    MinioDisputeEvidenceStorage(MinioClient client,
                               @Value("${minio.bucket.dispute-evidence}") String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    @CircuitBreaker(name = "minio", fallbackMethod = "storeFallback")
    @Retry(name = "minio")
    public String store(String objectKey, byte[] content, String contentType) {
        try (ByteArrayInputStream stream = new ByteArrayInputStream(content)) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(stream, content.length, -1)
                    .contentType(contentType)
                    .build());
            return objectKey;
        } catch (Exception e) {
            throw new DependencyFailureException("MinIO unavailable: failed to store dispute evidence " + objectKey, e);
        }
    }

    @Override
    @CircuitBreaker(name = "minio", fallbackMethod = "presignedGetUrlFallback")
    public String presignedGetUrl(String objectKey, Duration ttl) {
        try {
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry((int) ttl.toSeconds())
                    .build());
        } catch (Exception e) {
            throw new DependencyFailureException("MinIO unavailable: failed to presign dispute evidence " + objectKey, e);
        }
    }

    // --- Fallback methods ---

    private String storeFallback(String objectKey, byte[] content, String contentType, Throwable t) {
        throw new DependencyFailureException(
                "MinIO circuit breaker open: cannot store dispute evidence " + objectKey, t);
    }

    private String presignedGetUrlFallback(String objectKey, Duration ttl, Throwable t) {
        throw new DependencyFailureException(
                "MinIO circuit breaker open: cannot generate presigned URL for " + objectKey, t);
    }
}
