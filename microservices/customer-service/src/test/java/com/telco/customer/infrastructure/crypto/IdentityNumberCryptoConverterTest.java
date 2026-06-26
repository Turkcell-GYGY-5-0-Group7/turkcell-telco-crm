package com.telco.customer.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IdentityNumberCryptoConverterTest {

    // Base64-encoded 32-byte (AES-256) test key.
    private static final String TEST_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private IdentityNumberCryptoConverter converter;

    @BeforeEach
    void setUp() {
        converter = new IdentityNumberCryptoConverter(new AesKeyProvider(TEST_KEY));
    }

    @Test
    void encryptsToCiphertextDifferentFromPlaintext() {
        String plaintext = "10000000146";
        String ciphertext = converter.convertToDatabaseColumn(plaintext);

        assertThat(ciphertext).isNotNull().isNotEqualTo(plaintext);
        assertThat(ciphertext).doesNotContain(plaintext);
    }

    @Test
    void roundTripsPlaintext() {
        String plaintext = "10000000146";
        String ciphertext = converter.convertToDatabaseColumn(plaintext);
        assertThat(converter.convertToEntityAttribute(ciphertext)).isEqualTo(plaintext);
    }

    @Test
    void usesAFreshIvSoSameInputProducesDifferentCiphertext() {
        String plaintext = "10000000146";
        String first = converter.convertToDatabaseColumn(plaintext);
        String second = converter.convertToDatabaseColumn(plaintext);

        assertThat(first).isNotEqualTo(second);
        // Both still decrypt back to the same plaintext.
        assertThat(converter.convertToEntityAttribute(first)).isEqualTo(plaintext);
        assertThat(converter.convertToEntityAttribute(second)).isEqualTo(plaintext);
    }

    @Test
    void passesNullThrough() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void rejectsAKeyThatIsNotAes256() {
        // 16-byte key, base64-encoded -> must be rejected (we mandate AES-256).
        assertThatThrownBy(() -> new AesKeyProvider("MDEyMzQ1Njc4OWFiY2RlZg=="))
                .isInstanceOf(IllegalStateException.class);
    }
}
