package com.telco.platform.starter.observability.logging;

import ch.qos.logback.classic.pattern.ExtendedThrowableProxyConverter;
import ch.qos.logback.classic.spi.IThrowableProxy;
import com.telco.platform.common.masking.PiiMasker;

/**
 * Logback throwable converter that masks Turkish PII in rendered stack traces (ADR-021, Layer B).
 *
 * <p>Exception messages frequently embed identifiers (for example
 * {@code Customer 12345678901 not found}). The standard {@code %mask} converter only covers the log
 * message, so this converter masks the full throwable rendering (message lines and causes) as a
 * backstop. Use it as {@code %maskEx} in place of {@code %ex}/{@code %wEx} in the pattern.
 */
public class PiiMaskingThrowableConverter extends ExtendedThrowableProxyConverter {

    @Override
    protected String throwableProxyToString(IThrowableProxy tp) {
        return PiiMasker.maskFreeText(super.throwableProxyToString(tp));
    }
}
