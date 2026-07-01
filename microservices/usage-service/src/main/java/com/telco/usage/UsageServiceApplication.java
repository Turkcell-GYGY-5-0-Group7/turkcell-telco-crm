package com.telco.usage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * usage-service (CQRS + Mediator, port 9006).
 * CDR ingestion, quota metering, threshold events, overage capture, period aggregation.
 */
@SpringBootApplication
@EnableConfigurationProperties
public class UsageServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UsageServiceApplication.class, args);
    }
}
