package com.telco.billing.infrastructure.storage;

public interface StorageService {

    String store(String objectName, byte[] content, String contentType);

    byte[] fetch(String objectRef);
}
