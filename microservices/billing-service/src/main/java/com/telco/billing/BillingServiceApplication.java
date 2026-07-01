package com.telco.billing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** billing-service (Domain Orchestration, ADR-004). Invoice generation, bill-run, AC-02. */
@SpringBootApplication
@EnableScheduling
public class BillingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BillingServiceApplication.class, args);
    }
}
