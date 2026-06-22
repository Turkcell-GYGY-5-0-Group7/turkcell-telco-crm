package com.telco.platform.starter.observability.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.telco.platform.common.masking.PiiMasker;

/**
 * Logback backstop that masks well-known Turkish PII formats in rendered log messages
 * (Layer B of ADR-021): TCKN, MSISDN, IBAN, email, and PAN.
 *
 * <p>This is defense-in-depth for exception messages, third-party output, and developer free-text
 * that the {@code @Sensitive} masking {@code ObjectMapper} (Layer A) cannot reach structurally.
 * Logback initializes before the Spring context, so this converter is self-contained and always
 * masks every known pattern; fine-grained, per-field control is provided by Layer A.
 *
 * <p>Register it in {@code logback-spring.xml} and use the {@code %mask} word in the pattern:
 * <pre>{@code
 * <conversionRule conversionWord="mask"
 *     converterClass="com.telco.platform.starter.observability.logging.PiiMaskingConverter"/>
 * <pattern>%d{ISO8601} %-5level [%X{correlationId}] %logger - %mask</pattern>
 * }</pre>
 */
public class PiiMaskingConverter extends ClassicConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return PiiMasker.maskFreeText(event.getFormattedMessage());
    }
}
