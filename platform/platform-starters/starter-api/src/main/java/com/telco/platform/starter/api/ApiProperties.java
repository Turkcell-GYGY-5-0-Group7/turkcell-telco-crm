package com.telco.platform.starter.api;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the API starter (prefix {@code telco.platform.api}).
 */
@ConfigurationProperties(prefix = "telco.platform.api")
public class ApiProperties {

    /** Whether the global exception handler is registered. Defaults to {@code true}. */
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
