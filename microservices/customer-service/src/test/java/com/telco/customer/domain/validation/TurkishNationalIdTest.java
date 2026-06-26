package com.telco.customer.domain.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TurkishNationalIdTest {

    @ParameterizedTest
    @ValueSource(strings = {"10000000146", "11111111110"})
    void acceptsChecksumValidTckn(String tckn) {
        assertTrue(TurkishNationalId.isValidTckn(tckn));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "10000000145",   // wrong final checksum digit
            "12345678901",   // checksum invalid
            "00000000000",   // leading zero (and invalid)
            "1234567890",    // 10 digits - too short
            "123456789012",  // 12 digits - too long
            "1000000014A"    // non-digit
    })
    void rejectsInvalidTckn(String tckn) {
        assertFalse(TurkishNationalId.isValidTckn(tckn));
    }

    @Test
    void rejectsNullTckn() {
        assertFalse(TurkishNationalId.isValidTckn(null));
    }

    @Test
    void acceptsChecksumValidVkn() {
        assertTrue(TurkishNationalId.isValidVkn("1234567890"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1234567891",   // wrong checksum digit
            "123456789",    // 9 digits - too short
            "12345678901",  // 11 digits - too long
            "123456789X"    // non-digit
    })
    void rejectsInvalidVkn(String vkn) {
        assertFalse(TurkishNationalId.isValidVkn(vkn));
    }
}
