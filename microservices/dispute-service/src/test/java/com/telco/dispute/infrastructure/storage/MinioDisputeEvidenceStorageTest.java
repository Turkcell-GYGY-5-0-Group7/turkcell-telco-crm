package com.telco.dispute.infrastructure.storage;

import com.telco.platform.common.exception.DependencyFailureException;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinioDisputeEvidenceStorageTest {

    @Mock private MinioClient client;

    private MinioDisputeEvidenceStorage storage;

    @BeforeEach
    void setUp() {
        storage = new MinioDisputeEvidenceStorage(client, "dispute-evidence");
    }

    @Test
    void store_returns_the_object_key_on_success() throws Exception {
        String key = storage.store("d-1/receipt.pdf", "hello".getBytes(), "application/pdf");

        assertThat(key).isEqualTo("d-1/receipt.pdf");
    }

    @Test
    void store_wraps_a_minio_failure_as_dependency_failure_exception() throws Exception {
        when(client.putObject(any())).thenThrow(new RuntimeException("MinIO down"));

        assertThatThrownBy(() -> storage.store("d-1/receipt.pdf", "hello".getBytes(), "application/pdf"))
                .isInstanceOf(DependencyFailureException.class);
    }

    @Test
    void presignedGetUrl_returns_the_client_generated_url_on_success() throws Exception {
        when(client.getPresignedObjectUrl(any())).thenReturn("https://minio.local/presigned");

        String url = storage.presignedGetUrl("d-1/receipt.pdf", Duration.ofMinutes(15));

        assertThat(url).isEqualTo("https://minio.local/presigned");
    }

    @Test
    void presignedGetUrl_wraps_a_minio_failure_as_dependency_failure_exception() throws Exception {
        when(client.getPresignedObjectUrl(any())).thenThrow(new RuntimeException("MinIO down"));

        assertThatThrownBy(() -> storage.presignedGetUrl("d-1/receipt.pdf", Duration.ofMinutes(15)))
                .isInstanceOf(DependencyFailureException.class);
    }
}
