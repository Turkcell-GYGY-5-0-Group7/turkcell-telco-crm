package com.telco.platform.starter.logpersistence;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for optional DB log persistence (prefix {@code telco.platform.logging.persistence}).
 *
 * <p>Disabled by default: production relies on structured logs to Loki plus traceId/correlationId
 * (ADR-012, ADR-015). Enable in local/test profiles to persist request/exception logs and resolve
 * the {@code logId} returned in error responses.
 */
@ConfigurationProperties(prefix = "telco.platform.logging.persistence")
public class LogPersistenceProperties {

    /** Master switch for DB log persistence. Off by default. */
    private boolean enabled = false;

    /** Request-log table name. */
    private String requestTable = "request_logs";

    /** Exception-log table name. */
    private String exceptionTable = "exception_logs";

    /** Whether to also persist a row per HTTP request (for non-mediator services). */
    private boolean httpEnabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRequestTable() {
        return requestTable;
    }

    public void setRequestTable(String requestTable) {
        this.requestTable = requestTable;
    }

    public String getExceptionTable() {
        return exceptionTable;
    }

    public void setExceptionTable(String exceptionTable) {
        this.exceptionTable = exceptionTable;
    }

    public boolean isHttpEnabled() {
        return httpEnabled;
    }

    public void setHttpEnabled(boolean httpEnabled) {
        this.httpEnabled = httpEnabled;
    }
}
