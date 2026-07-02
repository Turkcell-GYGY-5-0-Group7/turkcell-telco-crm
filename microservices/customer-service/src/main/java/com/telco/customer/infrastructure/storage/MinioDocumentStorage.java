package com.telco.customer.infrastructure.storage;

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
 * MinIO/S3 adapter for {@link DocumentStorage} (ADR-006).
 *
 * <p>All outbound MinIO calls are guarded by the {@code minio} circuit breaker. The {@code store}
 * operation also uses the {@code minio} retry policy (up to 2 attempts with exponential backoff)
 * to handle transient write failures. {@code presignedGetUrl} fails fast — a retry on URL
 * generation adds latency without benefit since the signature is computed locally against MinIO
 * metadata. Configuration is driven by the shared {@code application.yml} resilience4j block.
 */
@Component
class MinioDocumentStorage implements DocumentStorage {

    private final MinioClient client;
    private final String bucket;

    MinioDocumentStorage(MinioClient client,
                         @Value("${minio.bucket.kyc-documents}") String bucket) {
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
            throw new DependencyFailureException("MinIO unavailable: failed to store KYC document " + objectKey, e);
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
            throw new DependencyFailureException("MinIO unavailable: failed to presign KYC document " + objectKey, e);
        }
    }

    // --- Fallback methods ---

    private String storeFallback(String objectKey, byte[] content, String contentType, Throwable t) {
        throw new DependencyFailureException(
                "MinIO circuit breaker open: cannot store KYC document " + objectKey, t);
    }

    private String presignedGetUrlFallback(String objectKey, Duration ttl, Throwable t) {
        throw new DependencyFailureException(
                "MinIO circuit breaker open: cannot generate presigned URL for " + objectKey, t);
    }
}
