package com.telco.template;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Service template entry point.
 *
 * <p>Architecture Mode: CQRS + MEDIATOR (ADR-004). Platform capabilities (mediator, API contract,
 * security, observability) are contributed by platform starters via auto-configuration; this class
 * only bootstraps Spring Boot.
 */
@SpringBootApplication
public class TemplateApplication {

    public static void main(String[] args) {
        SpringApplication.run(TemplateApplication.class, args);
    }
}
