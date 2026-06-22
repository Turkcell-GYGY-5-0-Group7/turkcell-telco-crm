package com.telco.platform.common.masking;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-Java masking algorithms shared by both masking layers (ADR-021):
 * Layer A (the {@code @Sensitive} Jackson serializer) and Layer B (the Logback backstop converter).
 *
 * <p>No Spring or Jackson dependency, so it can live in {@code platform-common} (ADR-020) and be
 * reused by the autoconfigure and observability modules alike. All methods are null-safe.
 */
public final class PiiMasker {

    /** Default mask character used when a caller does not specify one. */
    public static final char DEFAULT_MASK_CHAR = '*';

    /** Default number of trailing characters kept for {@link MaskStrategy#PARTIAL}. */
    public static final int DEFAULT_KEEP_LAST = 4;

    /** Well-known Turkish PII formats recognized by the free-text backstop (Layer B). */
    public enum PiiPattern {

        /** Standard email address. */
        EMAIL(Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")),

        /** Turkish IBAN: {@code TR} followed by 24 digits. */
        IBAN(Pattern.compile("(?i)\\bTR\\d{24}\\b")),

        /** Payment card number: 13-19 digits, optionally grouped by spaces or dashes. */
        PAN(Pattern.compile("(?<!\\d)(?:\\d{4}[ -]?){3}\\d{1,7}(?!\\d)")),

        /** Turkish mobile MSISDN, optionally prefixed with {@code +90} or {@code 0}. */
        MSISDN(Pattern.compile("(?<!\\d)(?:\\+90|0)?5\\d{9}(?!\\d)")),

        /** TCKN: 11 digits, not starting with 0, not embedded in a longer digit run. */
        TCKN(Pattern.compile("(?<!\\d)[1-9]\\d{10}(?!\\d)"));

        private final Pattern pattern;

        PiiPattern(Pattern pattern) {
            this.pattern = pattern;
        }

        Pattern pattern() {
            return pattern;
        }
    }

    private PiiMasker() {
    }

    /**
     * Masks a single field value according to the given strategy.
     *
     * @param raw       the value to mask; {@code null} and blank values are returned unchanged
     * @param strategy  masking strategy
     * @param keepLast  trailing characters to keep for {@link MaskStrategy#PARTIAL}; negative defers
     *                  to {@link #DEFAULT_KEEP_LAST}
     * @param maskChar  the character to mask with
     * @return the masked value
     */
    public static String maskValue(String raw, MaskStrategy strategy, int keepLast, char maskChar) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        MaskStrategy effective = strategy == null ? MaskStrategy.PARTIAL : strategy;
        return switch (effective) {
            case FULL -> repeat(maskChar, raw.length());
            case HASH -> sha256Hex(raw);
            case EMAIL -> maskEmail(raw, maskChar);
            case PARTIAL -> maskPartial(raw, keepLast < 0 ? DEFAULT_KEEP_LAST : keepLast, maskChar);
        };
    }

    /** Convenience overload using {@link #DEFAULT_MASK_CHAR}. */
    public static String maskValue(String raw, MaskStrategy strategy, int keepLast) {
        return maskValue(raw, strategy, keepLast, DEFAULT_MASK_CHAR);
    }

    /**
     * Applies the free-text backstop (Layer B) over arbitrary text, masking every enabled PII
     * pattern. Patterns are applied longest-format first so earlier masks break later digit runs.
     */
    public static String maskFreeText(String text, char maskChar, Set<PiiPattern> enabled) {
        if (text == null || text.isEmpty() || enabled == null || enabled.isEmpty()) {
            return text;
        }
        String result = text;
        for (PiiPattern p : PiiPattern.values()) {
            if (!enabled.contains(p)) {
                continue;
            }
            result = maskMatches(result, p, maskChar);
        }
        return result;
    }

    /** Convenience overload masking all known patterns with {@link #DEFAULT_MASK_CHAR}. */
    public static String maskFreeText(String text) {
        return maskFreeText(text, DEFAULT_MASK_CHAR, EnumSet.allOf(PiiPattern.class));
    }

    private static String maskMatches(String text, PiiPattern pattern, char maskChar) {
        Matcher matcher = pattern.pattern().matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String match = matcher.group();
            String masked = pattern == PiiPattern.EMAIL
                    ? maskEmail(match, maskChar)
                    : maskPartial(match, DEFAULT_KEEP_LAST, maskChar);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(masked));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String maskPartial(String raw, int keepLast, char maskChar) {
        int len = raw.length();
        int visible = len > keepLast ? keepLast : 0;
        int maskCount = len - visible;
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char c = raw.charAt(i);
            // Preserve grouping separators so masked numbers stay readable.
            if (i < maskCount && c != ' ' && c != '-') {
                sb.append(maskChar);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String maskEmail(String raw, char maskChar) {
        int at = raw.indexOf('@');
        if (at <= 0 || at == raw.length() - 1) {
            return maskPartial(raw, 0, maskChar);
        }
        String local = raw.substring(0, at);
        String domain = raw.substring(at + 1);
        String maskedLocal = local.charAt(0) + repeat(maskChar, Math.max(1, local.length() - 1));
        int lastDot = domain.lastIndexOf('.');
        String maskedDomain = lastDot > 0
                ? repeat(maskChar, 3) + domain.substring(lastDot)
                : repeat(maskChar, 3);
        return maskedLocal + "@" + maskedDomain;
    }

    private static String repeat(char c, int count) {
        return String.valueOf(c).repeat(Math.max(0, count));
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is mandated by the JLS; this is unreachable on any compliant JVM.
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
