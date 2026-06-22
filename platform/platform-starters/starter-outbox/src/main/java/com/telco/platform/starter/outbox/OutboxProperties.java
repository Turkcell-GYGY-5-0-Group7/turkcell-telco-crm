package com.telco.platform.starter.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the JDBC outbox starter (prefix {@code telco.platform.outbox}).
 */
@ConfigurationProperties(prefix = "telco.platform.outbox")
public class OutboxProperties {

    /** Whether outbox auto-configuration is active. */
    private boolean enabled = true;

    /** Outbox table name. */
    private String table = "outbox_event";

    /** Optional polling relay (Debezium is the primary delivery per ADR-005). */
    private final Relay relay = new Relay();

    /** Stuck-row sweeper that warns when rows stay NEW too long. */
    private final Sweeper sweeper = new Sweeper();

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

    public Relay getRelay() {
        return relay;
    }

    public Sweeper getSweeper() {
        return sweeper;
    }

    /** Settings for the optional polling relay, disabled by default. */
    public static class Relay {

        /** Whether the polling relay scheduler is active. Off by default; Debezium delivers. */
        private boolean enabled = false;

        /** Maximum rows fetched per relay poll. */
        private int batchSize = 100;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }

    /** Settings for the stuck-row sweeper, enabled by default (read-only monitoring). */
    public static class Sweeper {

        /** Whether the sweeper scheduler is active. */
        private boolean enabled = true;

        /** Rows in NEW older than this many milliseconds are considered stuck. */
        private long staleThresholdMs = 300_000L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getStaleThresholdMs() {
            return staleThresholdMs;
        }

        public void setStaleThresholdMs(long staleThresholdMs) {
            this.staleThresholdMs = staleThresholdMs;
        }
    }
}
