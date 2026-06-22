package com.telco.platform.common.masking;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as carrying personal data that must be masked in the log/persistence view (ADR-021).
 *
 * <p>The platform masking {@code ObjectMapper} (Layer A) honors this annotation when serializing an
 * object for logging. The default application {@code ObjectMapper} ignores it, so events and API
 * responses keep their real values; masking is a log-view concern, not a wire-view one.
 *
 * <p>Example:
 * <pre>{@code
 * public record RegisterCustomer(
 *         @Sensitive String tckn,
 *         @Sensitive(MaskStrategy.EMAIL) String email,
 *         String displayName) implements Command<CustomerId> { }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
public @interface Sensitive {

    /** Masking strategy for this field. Defaults to {@link MaskStrategy#PARTIAL}. */
    MaskStrategy value() default MaskStrategy.PARTIAL;

    /**
     * For {@link MaskStrategy#PARTIAL}, how many trailing characters to keep unmasked. A negative
     * value (the default) defers to the platform default ({@code telco.platform.logging.masking.keep-last}).
     */
    int keepLast() default -1;
}
