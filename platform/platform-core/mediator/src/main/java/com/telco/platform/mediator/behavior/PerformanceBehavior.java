package com.telco.platform.mediator.behavior;

import com.telco.platform.mediator.pipeline.PipelineBehavior;
import com.telco.platform.mediator.pipeline.PipelineOrder;
import com.telco.platform.mediator.pipeline.RequestHandlerDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Times the inner handler call and emits an slf4j warning when it exceeds the slow threshold.
 */
public final class PerformanceBehavior implements PipelineBehavior {

    private static final Logger LOGGER = LoggerFactory.getLogger(PerformanceBehavior.class);

    private final long slowThresholdMs;

    public PerformanceBehavior(long slowThresholdMs) {
        this.slowThresholdMs = slowThresholdMs;
    }

    @Override
    public boolean supports(Object request) {
        return true;
    }

    @Override
    public <R> R handle(Object request, RequestHandlerDelegate<R> next) {
        long start = System.nanoTime();
        try {
            return next.invoke();
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000L;
            if (durationMs >= slowThresholdMs) {
                LOGGER.warn("slow request type={} durationMs={} thresholdMs={}",
                        request.getClass().getSimpleName(), durationMs, slowThresholdMs);
            }
        }
    }

    @Override
    public int order() {
        return PipelineOrder.PERFORMANCE;
    }
}
