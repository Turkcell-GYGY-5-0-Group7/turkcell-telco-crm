package com.telco.customer.infrastructure.storage;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.time.Duration;

/** MinIO/S3 adapter for {@link DocumentStorage} (ADR-006). */
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
            throw new IllegalStateException("failed to store KYC document " + objectKey, e);
        }
    }

    @Override
    public String presignedGetUrl(String objectKey, Duration ttl) {
        try {
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry((int) ttl.toSeconds())
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("failed to presign KYC document " + objectKey, e);
        }
    }
}
