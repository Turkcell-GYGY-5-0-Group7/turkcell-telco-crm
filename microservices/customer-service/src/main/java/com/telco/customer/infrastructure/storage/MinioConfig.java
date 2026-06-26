package com.telco.customer.infrastructure.storage;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the MinIO client from {@code minio.*} configuration (ADR-006). */
@Configuration
class MinioConfig {

    @Bean
    MinioClient minioClient(@Value("${minio.endpoint}") String endpoint,
                            @Value("${minio.access-key}") String accessKey,
                            @Value("${minio.secret-key}") String secretKey) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
