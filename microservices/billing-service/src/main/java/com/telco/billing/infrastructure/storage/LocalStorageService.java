package com.telco.billing.infrastructure.storage;

import com.telco.platform.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@ConditionalOnProperty(name = "minio.enabled", havingValue = "false", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalStorageService.class);

    private final Path storageRoot;

    public LocalStorageService(
            @Value("${billing.local-storage.path:${java.io.tmpdir}/billing-pdfs}") String storagePath) {
        this.storageRoot = Path.of(storagePath);
        try {
            Files.createDirectories(storageRoot);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create local PDF storage directory", e);
        }
    }

    @Override
    public String store(String objectName, byte[] content, String contentType) {
        Path target = storageRoot.resolve(objectName);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            LOGGER.info("Stored PDF locally at {}", target);
            return "file://" + target.toAbsolutePath();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store PDF locally: " + objectName, e);
        }
    }

    @Override
    public byte[] fetch(String objectRef) {
        Path target = Path.of(objectRef.replace("file://", ""));
        if (!Files.exists(target)) {
            throw new ResourceNotFoundException("PDF not found at: " + objectRef);
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read PDF: " + objectRef, e);
        }
    }
}
