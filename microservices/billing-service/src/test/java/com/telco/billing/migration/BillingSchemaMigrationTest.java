package com.telco.billing.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class BillingSchemaMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Test
    void migrations_apply_cleanly_and_create_expected_schema() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/migration/platform")
                .load()
                .migrate();

        try (Connection c = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            DatabaseMetaData meta = c.getMetaData();

            for (String table : new String[]{
                    "invoices", "invoice_lines", "bill_cycles",
                    "subscriber_billing_records", "tariff_prices", "overage_records",
                    "outbox_event"}) {
                assertTrue(tableExists(meta, table), "table missing: " + table);
            }
        }
    }

    private static boolean tableExists(DatabaseMetaData meta, String table) throws Exception {
        try (ResultSet rs = meta.getTables(null, "public", table, new String[]{"TABLE"})) {
            return rs.next();
        }
    }
}
