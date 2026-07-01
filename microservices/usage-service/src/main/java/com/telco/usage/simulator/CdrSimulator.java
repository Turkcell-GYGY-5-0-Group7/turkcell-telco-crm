package com.telco.usage.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.usage.config.SimulatorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CDR simulator for load and integration testing (feature 10.6).
 *
 * <p>Only active when the {@code cdr-sim} Spring profile is set. Produces JSON CDR events to the
 * {@code cdr.events} Kafka topic at a rate of one event per 100ms. Exits after producing all
 * configured events.
 *
 * <p>KafkaTemplate is used directly here because this is a producer, not a domain operation;
 * no outbox is needed for simulation data.
 */
@Component
@Profile("cdr-sim")
public class CdrSimulator implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(CdrSimulator.class);
    private static final String CDR_EVENTS_TOPIC = "cdr.events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final SimulatorConfig config;
    private final ObjectMapper objectMapper;

    public CdrSimulator(KafkaTemplate<String, String> kafkaTemplate,
                        SimulatorConfig config,
                        ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        LOGGER.info("CDR simulator starting: subscriptions={} eventsPerSubscription={} type={} quantity={}",
                config.getSubscriptionIds(), config.getEventsPerSubscription(),
                config.getUsageType(), config.getQuantityPerEvent());

        int totalProduced = 0;

        for (String subscriptionId : config.getSubscriptionIds()) {
            for (int i = 0; i < config.getEventsPerSubscription(); i++) {
                String cdrRef = UUID.randomUUID().toString();

                Map<String, Object> payload = new HashMap<>();
                payload.put("subscriptionId", subscriptionId);
                payload.put("type", config.getUsageType());
                payload.put("quantity", config.getQuantityPerEvent());
                payload.put("occurredAt", Instant.now().toString());
                payload.put("cdrRef", cdrRef);

                String json = objectMapper.writeValueAsString(payload);
                kafkaTemplate.send(CDR_EVENTS_TOPIC, cdrRef, json);

                LOGGER.info("Produced CDR cdrRef={} subscriptionId={} type={} quantity={}",
                        cdrRef, subscriptionId, config.getUsageType(), config.getQuantityPerEvent());

                totalProduced++;
                Thread.sleep(100);
            }
        }

        LOGGER.info("CDR simulator finished: totalProduced={}", totalProduced);
    }
}
