package com.telco.platform.starter.inbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the JDBC inbox starter (prefix {@code telco.platform.inbox}).
 */
@ConfigurationProperties(prefix = "telco.platform.inbox")
public class InboxProperties {

    /** Whether inbox auto-configuration is active. */
    private boolean enabled = true;

    /** Inbox table name. */
    private String table = "inbox_message";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }
}
