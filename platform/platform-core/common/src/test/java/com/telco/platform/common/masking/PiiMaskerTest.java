package com.telco.platform.common.masking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PiiMaskerTest {

    @Test
    void partialKeepsTrailingCharacters() {
        assertEquals("*******8901", PiiMasker.maskValue("12345678901", MaskStrategy.PARTIAL, 4));
    }

    @Test
    void partialMasksEntirelyWhenShorterThanKeepLast() {
        assertEquals("***", PiiMasker.maskValue("abc", MaskStrategy.PARTIAL, 4));
    }

    @Test
    void fullMasksEveryCharacter() {
        assertEquals("*****", PiiMasker.maskValue("12345", MaskStrategy.FULL, -1));
    }

    @Test
    void hashIsStableAndHidesValue() {
        String first = PiiMasker.maskValue("12345678901", MaskStrategy.HASH, -1);
        String second = PiiMasker.maskValue("12345678901", MaskStrategy.HASH, -1);
        assertEquals(first, second);
        assertEquals(64, first.length());
        assertTrue(first.matches("[0-9a-f]{64}"));
    }

    @Test
    void emailMasksLocalAndDomainKeepingTld() {
        assertEquals("j***@***.com", PiiMasker.maskValue("john@telco.com", MaskStrategy.EMAIL, -1));
    }

    @Test
    void nullAndEmptyAreReturnedUnchanged() {
        assertEquals(null, PiiMasker.maskValue(null, MaskStrategy.FULL, -1));
        assertEquals("", PiiMasker.maskValue("", MaskStrategy.FULL, -1));
    }

    @Test
    void freeTextMasksTcknInExceptionMessage() {
        String masked = PiiMasker.maskFreeText("Customer with TCKN 12345678901 was not found");
        assertTrue(masked.contains("*******8901"), masked);
        assertTrue(!masked.contains("12345678901"), masked);
    }

    @Test
    void freeTextMasksEmailAndIban() {
        String masked = PiiMasker.maskFreeText("contact john@telco.com iban TR330006100519786457841326");
        assertTrue(masked.contains("j***@***.com"), masked);
        assertTrue(!masked.contains("TR330006100519786457841326"), masked);
    }
}
