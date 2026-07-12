package com.telco.platform.starter.lock;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Typed configuration for the distributed-lock starter under {@code telco.platform.lock} (ADR-024
 * Section 2).
 */
@ConfigurationProperties(prefix = "telco.platform.lock")
public class LockProperties {

    /** Master switch for the lock auto-configuration. Defaults to enabled. */
    private boolean enabled = true;

    /** Redisson connection settings. */
    private Redis redis = new Redis();

    /** Max time a caller blocks trying to acquire before failing (fail-closed). */
    private Duration waitTime = Duration.ofSeconds(5);

    /** Redisson's internal lock-watchdog-timeout, used when a call site's {@code leaseTime} is unset. */
    private Duration watchdogTimeout = Duration.ofSeconds(30);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Redis getRedis() {
        return redis;
    }

    public void setRedis(Redis redis) {
        this.redis = redis;
    }

    public Duration getWaitTime() {
        return waitTime;
    }

    public void setWaitTime(Duration waitTime) {
        this.waitTime = waitTime;
    }

    public Duration getWatchdogTimeout() {
        return watchdogTimeout;
    }

    public void setWatchdogTimeout(Duration watchdogTimeout) {
        this.watchdogTimeout = watchdogTimeout;
    }

    /** Redisson connection target. Falls back to {@code spring.data.redis.host}/{@code port} if unset. */
    public static class Redis {

        /** Redisson single-server address, e.g. {@code redis://localhost:6379}. Takes precedence over host/port. */
        private String address;

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }
    }
}
