package com.telco.customer.infrastructure.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * Supplies the AES-256 key used to encrypt PII at rest (NFR-06). The key is a base64-encoded 32-byte
 * value sourced from configuration ({@code customer.crypto.aes-key}); in staging/prod it is injected
 * from a Kubernetes Secret / Vault rather than carrying a default.
 *
 * <p>Key rotation is out of scope for the MVP: a single active key encrypts and decrypts. This is a
 * locally-built capability flagged for migration to a platform {@code starter-crypto} (see
 * docs/architecture/platform-capabilities.md Section 3).
 */
@Component
public class AesKeyProvider {

    private static final int AES_256_KEY_BYTES = 32;

    private final SecretKey key;

    public AesKeyProvider(@Value("${customer.crypto.aes-key}") String base64Key) {
        byte[] raw = Base64.getDecoder().decode(base64Key);
        if (raw.length != AES_256_KEY_BYTES) {
            throw new IllegalStateException(
                    "customer.crypto.aes-key must decode to 32 bytes (AES-256); got " + raw.length);
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public SecretKey key() {
        return key;
    }
}
