package com.telco.usage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;

import java.util.List;

/**
 * Configuration properties for the CDR simulator (feature 10.6).
 * Only active when the {@code cdr-sim} profile is enabled.
 */
@ConfigurationProperties(prefix = "telco.simulator")
@Profile("cdr-sim")
public class SimulatorConfig {

    private List<String> subscriptionIds = List.of("00000000-0000-0000-0000-000000000001");
    private int eventsPerSubscription = 20;
    private String usageType = "DATA";
    private long quantityPerEvent = 100;

    public List<String> getSubscriptionIds() {
        return subscriptionIds;
    }

    public void setSubscriptionIds(List<String> subscriptionIds) {
        this.subscriptionIds = subscriptionIds;
    }

    public int getEventsPerSubscription() {
        return eventsPerSubscription;
    }

    public void setEventsPerSubscription(int eventsPerSubscription) {
        this.eventsPerSubscription = eventsPerSubscription;
    }

    public String getUsageType() {
        return usageType;
    }

    public void setUsageType(String usageType) {
        this.usageType = usageType;
    }

    public long getQuantityPerEvent() {
        return quantityPerEvent;
    }

    public void setQuantityPerEvent(long quantityPerEvent) {
        this.quantityPerEvent = quantityPerEvent;
    }
}
