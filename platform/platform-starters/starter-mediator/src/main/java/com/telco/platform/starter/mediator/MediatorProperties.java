package com.telco.platform.starter.mediator;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Typed configuration for the mediator pipeline, bound from {@code telco.platform.mediator.*}.
 */
@ConfigurationProperties(prefix = "telco.platform.mediator")
public class MediatorProperties {

    @NestedConfigurationProperty
    private final Performance performance = new Performance();

    @NestedConfigurationProperty
    private final Logging logging = new Logging();

    /** Performance-behavior settings. */
    public Performance getPerformance() {
        return performance;
    }

    /** Logging-behavior settings. */
    public Logging getLogging() {
        return logging;
    }

    /** Settings for the performance-monitoring behavior. */
    public static class Performance {

        /** Requests slower than this many milliseconds are logged as slow. */
        private long slowThresholdMs = 500L;

        public long getSlowThresholdMs() {
            return slowThresholdMs;
        }

        public void setSlowThresholdMs(long slowThresholdMs) {
            this.slowThresholdMs = slowThresholdMs;
        }
    }

    /** Settings for the request-logging behavior. */
    public static class Logging {

        /** Whether the logging behavior is registered. */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
