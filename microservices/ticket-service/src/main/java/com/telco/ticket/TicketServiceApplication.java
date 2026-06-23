package com.telco.ticket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** ticket-service (CQRS+Mediator; PostgreSQL). Skeleton only. */
@SpringBootApplication
public class TicketServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketServiceApplication.class, args);
    }
}
