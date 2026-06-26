package com.telco.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * payment-service (Domain Orchestration, port 9008).
 * Features 8.4-8.5: CQRS+Mediator handlers, mock-PSP with circuit breaker, inbox consumer
 * for order.created.v1, 24/72/168h retry scheduler, and payment events via outbox.
 */
@SpringBootApplication
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
