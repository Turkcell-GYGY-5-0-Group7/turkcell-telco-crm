package com.telco.ticket.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.import=",
                "spring.cloud.config.enabled=false"
        }
)
@ActiveProfiles("test")
@Testcontainers
class TicketSchemaMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:19999");
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void all_tables_exist() {
        for (String table : new String[]{"tickets", "ticket_comments", "sla_policies",
                "outbox_events", "inbox_messages"}) {
            Long count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name=?",
                    Long.class, table);
            assertThat(count).as("table %s should exist", table).isEqualTo(1L);
        }
    }

    @Test
    void sla_policies_seeded() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM sla_policies", Long.class);
        assertThat(count).isGreaterThanOrEqualTo(9L);
    }
}
