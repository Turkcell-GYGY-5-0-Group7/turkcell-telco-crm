package com.telco.platform.autoconfigure.masking;

import com.telco.platform.common.masking.MaskStrategy;
import com.telco.platform.common.masking.PiiMasker;
import com.telco.platform.common.masking.PiiMasker.PiiPattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.EnumSet;
import java.util.Set;

/**
 * Typed configuration for PII masking under {@code telco.platform.logging.masking} (ADR-021).
 *
 * <p>Controls Layer A (the {@code @Sensitive} masking {@code ObjectMapper}) and the pattern toggles
 * used by Layer B (the Logback backstop). Masking applies to the log/persistence view only; event
 * payloads and API responses are never affected.
 */
@ConfigurationProperties(prefix = "telco.platform.logging.masking")
public class MaskingProperties {

    /** Master switch for masking. Defaults to {@code true}. */
    private boolean enabled = true;

    /** Strategy used for the free-text backstop and as the documented platform default. */
    private MaskStrategy defaultStrategy = MaskStrategy.PARTIAL;

    /** Character used to mask values. Defaults to {@code *}. */
    private char maskChar = PiiMasker.DEFAULT_MASK_CHAR;

    /** Trailing characters kept for {@link MaskStrategy#PARTIAL} when a field does not override it. */
    private int keepLast = PiiMasker.DEFAULT_KEEP_LAST;

    /** Free-text backstop pattern toggles (Layer B). */
    @NestedConfigurationProperty
    private final Patterns patterns = new Patterns();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public MaskStrategy getDefaultStrategy() {
        return defaultStrategy;
    }

    public void setDefaultStrategy(MaskStrategy defaultStrategy) {
        this.defaultStrategy = defaultStrategy;
    }

    public char getMaskChar() {
        return maskChar;
    }

    public void setMaskChar(char maskChar) {
        this.maskChar = maskChar;
    }

    public int getKeepLast() {
        return keepLast;
    }

    public void setKeepLast(int keepLast) {
        this.keepLast = keepLast;
    }

    public Patterns getPatterns() {
        return patterns;
    }

    /** Resolves the enabled backstop patterns into a set for {@link PiiMasker#maskFreeText}. */
    public Set<PiiPattern> enabledPatterns() {
        Set<PiiPattern> set = EnumSet.noneOf(PiiPattern.class);
        if (patterns.tckn) {
            set.add(PiiPattern.TCKN);
        }
        if (patterns.msisdn) {
            set.add(PiiPattern.MSISDN);
        }
        if (patterns.iban) {
            set.add(PiiPattern.IBAN);
        }
        if (patterns.email) {
            set.add(PiiPattern.EMAIL);
        }
        if (patterns.pan) {
            set.add(PiiPattern.PAN);
        }
        return set;
    }

    /** Toggles for the free-text backstop patterns; all on by default. */
    public static class Patterns {

        /** TCKN (Turkish national id). */
        private boolean tckn = true;

        /** Turkish mobile MSISDN. */
        private boolean msisdn = true;

        /** Turkish IBAN. */
        private boolean iban = true;

        /** Email address. */
        private boolean email = true;

        /** Payment card number (PAN). */
        private boolean pan = true;

        public boolean isTckn() {
            return tckn;
        }

        public void setTckn(boolean tckn) {
            this.tckn = tckn;
        }

        public boolean isMsisdn() {
            return msisdn;
        }

        public void setMsisdn(boolean msisdn) {
            this.msisdn = msisdn;
        }

        public boolean isIban() {
            return iban;
        }

        public void setIban(boolean iban) {
            this.iban = iban;
        }

        public boolean isEmail() {
            return email;
        }

        public void setEmail(boolean email) {
            this.email = email;
        }

        public boolean isPan() {
            return pan;
        }

        public void setPan(boolean pan) {
            this.pan = pan;
        }
    }
}
