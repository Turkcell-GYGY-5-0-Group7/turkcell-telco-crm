package com.telco.reference;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Reference service entry point.
 *
 * <p>Architecture Mode: CQRS + MEDIATOR (ADR-004). Demonstrates the full platform path:
 * controller -> mediator -> command/query handler -> JPA aggregate, with domain events published
 * through the transactional outbox (ADR-005, ADR-009).
 */
@SpringBootApplication
public class ReferenceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReferenceServiceApplication.class, args);
    }
}
