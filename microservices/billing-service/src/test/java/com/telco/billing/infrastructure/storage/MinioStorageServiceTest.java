package com.telco.billing.infrastructure.storage;

import com.telco.platform.common.exception.DependencyFailureException;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import okhttp3.Headers;
import okhttp3.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the circuit-breaker-guarded MinIO adapter, including the fallback methods that
 * only Resilience4j itself would otherwise invoke once the "minio" circuit trips open (14.3.2 saga-
 * path coverage: timeout/retry-exhaustion branches for the outbound storage dependency).
 */
@ExtendWith(MockitoExtension.class)
class MinioStorageServiceTest {

    private static final String BUCKET = "invoice-pdfs";

    @Mock private MinioClient minioClient;

    private MinioStorageService service;

    @BeforeEach
    void setUp() {
        service = new MinioStorageService(minioClient, BUCKET);
    }

    @Test
    void store_uploads_object_and_returns_minio_reference() throws Exception {
        String ref = service.store("invoices/abc.pdf", "hello".getBytes(StandardCharsets.UTF_8), "application/pdf");

        assertThat(ref).isEqualTo("minio://" + BUCKET + "/invoices/abc.pdf");
    }

    @Test
    void store_wraps_minio_failure_as_dependency_failure_exception() throws Exception {
        doThrow(new RuntimeException("connection refused"))
                .when(minioClient).putObject(any());

        assertThatThrownBy(() -> service.store("invoices/abc.pdf", "hi".getBytes(StandardCharsets.UTF_8), "application/pdf"))
                .isInstanceOf(DependencyFailureException.class)
                .hasMessageContaining("Failed to store PDF in MinIO");
    }

    @Test
    void fetch_reads_object_bytes_from_minio() throws Exception {
        byte[] content = "pdf-bytes".getBytes(StandardCharsets.UTF_8);
        GetObjectResponse response = new GetObjectResponse(
                new Headers.Builder().build(), BUCKET, null, "invoices/abc.pdf",
                new ByteArrayInputStream(content));
        when(minioClient.getObject(any())).thenReturn(response);

        byte[] fetched = service.fetch("minio://" + BUCKET + "/invoices/abc.pdf");

        assertThat(fetched).isEqualTo(content);
    }

    @Test
    void fetch_wraps_minio_failure_as_dependency_failure_exception() throws Exception {
        when(minioClient.getObject(any())).thenThrow(new RuntimeException("not found"));

        assertThatThrownBy(() -> service.fetch("minio://" + BUCKET + "/invoices/missing.pdf"))
                .isInstanceOf(DependencyFailureException.class)
                .hasMessageContaining("Failed to fetch PDF from MinIO");
    }

    @Test
    void storeFallback_throws_dependency_failure_when_circuit_is_open() throws Exception {
        Method fallback = MinioStorageService.class.getDeclaredMethod(
                "storeFallback", String.class, byte[].class, String.class, Throwable.class);
        fallback.setAccessible(true);

        assertThatThrownBy(() -> invoke(fallback, service,
                "invoices/abc.pdf", "x".getBytes(StandardCharsets.UTF_8), "application/pdf",
                new RuntimeException("circuit open")))
                .hasCauseInstanceOf(DependencyFailureException.class);
    }

    @Test
    void fetchFallback_throws_dependency_failure_when_circuit_is_open() throws Exception {
        Method fallback = MinioStorageService.class.getDeclaredMethod(
                "fetchFallback", String.class, Throwable.class);
        fallback.setAccessible(true);

        assertThatThrownBy(() -> invoke(fallback, service,
                "minio://" + BUCKET + "/invoices/abc.pdf", new RuntimeException("circuit open")))
                .hasCauseInstanceOf(DependencyFailureException.class);
    }

    /** Reflection helper: unwraps {@link java.lang.reflect.InvocationTargetException} so assertions
     *  can match the real thrown exception type via {@code hasCauseInstanceOf}. */
    private static void invoke(Method method, Object target, Object... args) throws Throwable {
        try {
            method.invoke(target, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new RuntimeException("fallback invocation failed", e.getCause());
        }
    }
}
