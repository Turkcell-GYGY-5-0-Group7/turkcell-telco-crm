package com.telco.template.application;

import com.telco.platform.cqrs.QueryHandler;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Handles {@link PingQuery}. Registered automatically by starter-mediator (resolved by generics).
 * Query handlers MUST NOT change state (ADR-008).
 */
@Component
public class PingQueryHandler implements QueryHandler<PingQuery, PingResponse> {

    private final String serviceName;

    public PingQueryHandler(Environment environment) {
        this.serviceName = environment.getProperty("spring.application.name", "service-template");
    }

    @Override
    public PingResponse handle(PingQuery query) {
        return new PingResponse("UP", serviceName, Instant.now());
    }
}
