package com.telco.fraud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * fraud-service entry point.
 *
 * <p>Architecture Mode: CQRS + MEDIATOR (ADR-004, ADR-029 Decision Section 2). Structurally
 * identical to usage-service: consume subscription-service domain events via the inbox, evaluate
 * threshold-based rules, publish signal events via the outbox. Platform capabilities (mediator, API
 * contract, security, observability, outbox, inbox) are contributed by platform starters via
 * auto-configuration; this class only bootstraps Spring Boot.
 *
 * <p>This is the Feature 23.1 scaffold: schema, aggregates, and repositories only. Rule evaluation
 * (23.2), the fraud-case API (23.3), and outbox/inbox eventing wiring (23.2/23.4) are not present
 * yet - fraud-service is read-only relative to subscription-service and never accesses
 * {@code subscription-db} directly (ADR-006, ADR-029 Section 1).
 */
@SpringBootApplication
public class FraudServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FraudServiceApplication.class, args);
    }
}
