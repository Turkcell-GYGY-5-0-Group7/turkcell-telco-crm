package com.telco.billing.infrastructure.storage;

import com.telco.platform.common.exception.DependencyFailureException;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Component
@ConditionalOnProperty(name = "minio.enabled", havingValue = "true", matchIfMissing = false)
public class MinioStorageService implements StorageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinioStorageService.class);

    private final MinioClient minioClient;
    private final String bucket;

    public MinioStorageService(MinioClient minioClient,
                               @org.springframework.beans.factory.annotation.Value("${minio.bucket.invoice-pdfs}")
                               String bucket) {
        this.minioClient = minioClient;
        this.bucket = bucket;
    }

    @Override
    public String store(String objectName, byte[] content, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType(contentType)
                    .build());
            LOGGER.info("Stored PDF objectName={} bucket={}", objectName, bucket);
            return "minio://" + bucket + "/" + objectName;
        } catch (Exception e) {
            throw new DependencyFailureException("Failed to store PDF in MinIO: " + objectName, e);
        }
    }

    @Override
    public byte[] fetch(String objectRef) {
        String objectName = objectRef.replace("minio://" + bucket + "/", "");
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucket).object(objectName).build())) {
            return stream.readAllBytes();
        } catch (Exception e) {
            throw new DependencyFailureException("Failed to fetch PDF from MinIO: " + objectRef, e);
        }
    }
}
