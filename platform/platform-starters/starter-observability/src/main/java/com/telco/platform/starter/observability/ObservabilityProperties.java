package com.telco.platform.starter.observability;

import com.telco.platform.common.context.CorrelationConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Typed configuration for the observability starter ({@code telco.platform.observability.*}).
 *
 * <p>Controls correlation propagation as described by ADR-012 and ADR-015. Tracing wiring is
 * activated separately by the presence of micrometer-tracing on the classpath.
 */
@ConfigurationProperties(prefix = "telco.platform.observability")
public class ObservabilityProperties {

    /** Correlation propagation settings. */
    @NestedConfigurationProperty
    private final Correlation correlation = new Correlation();

    /** Returns the correlation propagation settings. */
    public Correlation getCorrelation() {
        return correlation;
    }

    /**
     * Correlation filter settings.
     */
    public static class Correlation {

        /** Whether the {@link CorrelationFilter} is registered. Defaults to {@code true}. */
        private boolean enabled = true;

        /** Inbound/outbound correlation header name. Defaults to {@code X-Correlation-Id}. */
        private String header = CorrelationConstants.HEADER_CORRELATION_ID;

        /** Returns whether correlation propagation is enabled. */
        public boolean isEnabled() {
            return enabled;
        }

        /** Sets whether correlation propagation is enabled. */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** Returns the correlation header name. */
        public String getHeader() {
            return header;
        }

        /** Sets the correlation header name. */
        public void setHeader(String header) {
            this.header = header;
        }
    }
}
