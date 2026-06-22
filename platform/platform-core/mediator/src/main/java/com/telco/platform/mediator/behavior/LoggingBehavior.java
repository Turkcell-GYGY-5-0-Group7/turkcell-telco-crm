package com.telco.platform.mediator.behavior;

import com.telco.platform.common.context.CorrelationContext;
import com.telco.platform.common.context.CorrelationContextHolder;
import com.telco.platform.common.context.UserContext;
import com.telco.platform.common.context.UserContextHolder;
import com.telco.platform.cqrs.Command;
import com.telco.platform.cqrs.Event;
import com.telco.platform.cqrs.Query;
import com.telco.platform.common.exception.PlatformException;
import com.telco.platform.mediator.behavior.support.NotLoggable;
import com.telco.platform.mediator.behavior.support.RequestLogEntry;
import com.telco.platform.mediator.behavior.support.RequestLogWriter;
import com.telco.platform.mediator.pipeline.PipelineBehavior;
import com.telco.platform.mediator.pipeline.PipelineOrder;
import com.telco.platform.mediator.pipeline.RequestHandlerDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

/**
 * Logs request start/end via slf4j and forwards a {@link RequestLogEntry} to each writer.
 * Requests implementing {@link NotLoggable} are skipped.
 */
public final class LoggingBehavior implements PipelineBehavior {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingBehavior.class);

    private final String serviceName;
    private final List<RequestLogWriter> writers;

    public LoggingBehavior(String serviceName, List<RequestLogWriter> writers) {
        this.serviceName = serviceName;
        this.writers = List.copyOf(writers);
    }

    @Override
    public boolean supports(Object request) {
        return !(request instanceof NotLoggable);
    }

    @Override
    public <R> R handle(Object request, RequestHandlerDelegate<R> next) {
        String requestType = request.getClass().getSimpleName();
        String requestKind = kindOf(request);
        long start = System.nanoTime();
        LOGGER.info("handling {} {}", requestKind, requestType);
        boolean success = false;
        String errorCode = null;
        try {
            R result = next.invoke();
            success = true;
            return result;
        } catch (PlatformException ex) {
            errorCode = ex.code().code();
            throw ex;
        } catch (RuntimeException ex) {
            errorCode = "INTERNAL_ERROR";
            throw ex;
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000L;
            LOGGER.info("handled {} {} success={} durationMs={}", requestKind, requestType, success, durationMs);
            emit(requestType, requestKind, durationMs, success, errorCode);
        }
    }

    private void emit(String requestType, String requestKind, long durationMs, boolean success, String errorCode) {
        if (writers.isEmpty()) {
            return;
        }
        String userId = UserContextHolder.get().map(UserContext::userId).orElse(null);
        String correlationId = CorrelationContextHolder.get()
                .map(CorrelationContext::correlationId).orElse(null);
        RequestLogEntry entry = new RequestLogEntry(serviceName, requestType, requestKind, userId,
                correlationId, durationMs, success, errorCode, Instant.now());
        for (RequestLogWriter writer : writers) {
            writer.write(entry);
        }
    }

    private static String kindOf(Object request) {
        if (request instanceof Command<?>) {
            return "COMMAND";
        }
        if (request instanceof Query<?>) {
            return "QUERY";
        }
        if (request instanceof Event) {
            return "EVENT";
        }
        return "REQUEST";
    }

    @Override
    public int order() {
        return PipelineOrder.LOGGING;
    }
}
