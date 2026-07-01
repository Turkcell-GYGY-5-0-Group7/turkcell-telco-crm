package com.telco.subscription;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** subscription-service (CQRS + Mediator): subscription lifecycle, MSISDN pool, SIM-card inventory, audit. */
@SpringBootApplication
public class SubscriptionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SubscriptionServiceApplication.class, args);
    }
}
