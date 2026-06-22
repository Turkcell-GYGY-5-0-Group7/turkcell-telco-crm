package com.telco.platform.mediator.behavior;

import com.telco.platform.mediator.behavior.support.RequestLogEntry;
import com.telco.platform.mediator.behavior.support.RequestLogWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link RequestLogWriter} that emits structured request log entries via slf4j.
 */
public final class Slf4jRequestLogWriter implements RequestLogWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(Slf4jRequestLogWriter.class);

    @Override
    public void write(RequestLogEntry entry) {
        LOGGER.info("request service={} type={} kind={} userId={} correlationId={} durationMs={} success={} errorCode={}",
                entry.service(), entry.requestType(), entry.requestKind(), entry.userId(),
                entry.correlationId(), entry.durationMs(), entry.success(), entry.errorCode());
    }
}
