package com.telco.customer.infrastructure.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * JPA converter that encrypts the national identity number (TCKN/VKN) with AES-GCM on write and
 * decrypts on read (NFR-06, ADR-021). The database column {@code identity_number_enc} therefore holds
 * ciphertext; the domain attribute holds plaintext.
 *
 * <p>Each encryption uses a fresh random 12-byte IV, which is prepended to the ciphertext (which itself
 * carries the GCM authentication tag) and the whole is base64-encoded for storage. AES-GCM provides
 * confidentiality and integrity; a tampered ciphertext fails to decrypt.
 *
 * <p>Instantiated by Hibernate but resolved as a Spring bean via {@code SpringBeanContainer} so the
 * {@link AesKeyProvider} is injected. Referenced explicitly with {@code @Convert} on the entity field
 * (no {@code autoApply}). Locally-built; flagged for a platform {@code starter-crypto}.
 */
@Component
@Converter
public class IdentityNumberCryptoConverter implements AttributeConverter<String, String> {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final AesKeyProvider keyProvider;
    private final SecureRandom secureRandom = new SecureRandom();

    public IdentityNumberCryptoConverter(AesKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keyProvider.key(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            byte[] combined = ByteBuffer.allocate(iv.length + ciphertext.length)
                    .put(iv)
                    .put(ciphertext)
                    .array();
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("identity number encryption failed", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(dbData);
            ByteBuffer buffer = ByteBuffer.wrap(combined);

            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keyProvider.key(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("identity number decryption failed", e);
        }
    }
}
