package com.telco.campaign.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class CampaignSchemaMigrationTest {

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
                    "campaigns", "campaign_tariff_codes", "campaign_redemptions",
                    "outbox_event", "inbox_message"}) {
                assertTrue(tableExists(meta, table), "table missing: " + table);
            }

            Set<String> tariffCodeRefs = importedTables(meta, "campaign_tariff_codes");
            assertTrue(tariffCodeRefs.contains("campaigns"),
                    "campaign_tariff_codes missing FK to campaigns");

            Set<String> redemptionRefs = importedTables(meta, "campaign_redemptions");
            assertTrue(redemptionRefs.contains("campaigns"),
                    "campaign_redemptions missing FK to campaigns");

            // ADR-006 exit criterion ("no shared database access between campaign-service and
            // product-catalog-service/order-service"): campaign-db's own Flyway migration set must
            // never create another service's tables. This is the automated, direct proof that
            // campaign-service's schema stays isolated - the database-level access control (dedicated
            // `campaign` Postgres role, no grants to any other service's role) is enforced separately
            // in infra/docker/postgres/initdb/01-create-databases.sql.
            for (String foreignTable : new String[]{
                    "tariffs", "addons", "tariff_versions", "tariff_addons",
                    "orders", "order_items", "saga_state"}) {
                assertTrue(!tableExists(meta, foreignTable),
                        "campaign-db must not contain another service's table: " + foreignTable
                                + " (ADR-006 database-per-service violation)");
            }
        }
    }

    private static boolean tableExists(DatabaseMetaData meta, String table) throws Exception {
        try (ResultSet rs = meta.getTables(null, "public", table, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private static Set<String> importedTables(DatabaseMetaData meta, String table) throws Exception {
        Set<String> refs = new HashSet<>();
        try (ResultSet rs = meta.getImportedKeys(null, "public", table)) {
            while (rs.next()) {
                refs.add(rs.getString("PKTABLE_NAME"));
            }
        }
        return refs;
    }
}
