package com.telco.notification.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.telco.notification.infrastructure.persistence.CommunicationPreferenceRepository;
import com.telco.notification.infrastructure.persistence.NotificationRepository;
import com.telco.notification.infrastructure.persistence.NotificationTemplateRepository;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 12.1.2 / 12.6 schema parity: notification-service co-locates Postgres solely for the platform
 * outbox/inbox tables (its business store is Mongo). This mirrors ticket-service's
 * TicketSchemaMigrationTest and asserts the platform migrations (V900 outbox, V901 inbox) applied.
 *
 * <p>Mongo autoconfiguration is excluded so this stays a lightweight Postgres-only check that does not
 * require a Mongo container.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.import=",
                "spring.cloud.config.enabled=false",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration,"
                        + "org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration,"
                        + "org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration"
        }
)
@ActiveProfiles("test")
@Testcontainers
class NotificationSchemaMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:19999");
    }

    // Mongo autoconfig is excluded; mock the Mongo-backed repositories so the context still wires.
    @MockitoBean private NotificationRepository notificationRepository;
    @MockitoBean private NotificationTemplateRepository templateRepository;
    @MockitoBean private CommunicationPreferenceRepository preferenceRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void platform_outbox_and_inbox_tables_exist() {
        for (String table : new String[]{"outbox_event", "inbox_message"}) {
            Long count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name=?",
                    Long.class, table);
            assertThat(count).as("table %s should exist", table).isEqualTo(1L);
        }
    }
}
