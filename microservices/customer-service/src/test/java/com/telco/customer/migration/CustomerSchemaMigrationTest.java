package com.telco.customer.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the customer Flyway migrations apply cleanly on a real PostgreSQL container and produce the
 * expected customer master, address, document and audit schema (feature 6.1.2, ADR-013, ADR-016).
 *
 * <p>Runs Flyway directly against the container rather than booting Spring: the migration set is the
 * unit under test, and this avoids the config-server / Eureka bootstrap that is unavailable in tests.
 * It mirrors the production {@code spring.flyway.locations} so the platform outbox migration is
 * exercised alongside the service schema.
 */
@Testcontainers
class CustomerSchemaMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17");

    @Test
    void migrationsApplyCleanlyAndCreateSchema() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/migration/platform")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            DatabaseMetaData metaData = connection.getMetaData();

            for (String table : new String[] {
                    "customers", "addresses", "documents", "audit_log"}) {
                assertTrue(tableExists(metaData, table), "table missing: " + table);
            }

            // The platform outbox migration (V900) must apply alongside the service schema.
            assertTrue(tableExists(metaData, "outbox_event"), "platform outbox table missing");

            assertTrue(importedTables(metaData, "addresses").contains("customers"),
                    "addresses is missing the foreign key to customers");
            assertTrue(importedTables(metaData, "documents").contains("customers"),
                    "documents is missing the foreign key to customers");
        }
    }

    private static boolean tableExists(DatabaseMetaData metaData, String table) throws Exception {
        try (ResultSet rs = metaData.getTables(null, "public", table, new String[] {"TABLE"})) {
            return rs.next();
        }
    }

    private static Set<String> importedTables(DatabaseMetaData metaData, String table) throws Exception {
        Set<String> referenced = new HashSet<>();
        try (ResultSet rs = metaData.getImportedKeys(null, "public", table)) {
            while (rs.next()) {
                referenced.add(rs.getString("PKTABLE_NAME"));
            }
        }
        return referenced;
    }
}
