package com.telco.catalog.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class CatalogSchemaMigrationTest {

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

            for (String table : new String[]{"tariffs", "addons", "tariff_versions", "tariff_addons", "outbox_event"}) {
                assertTrue(tableExists(meta, table), "table missing: " + table);
            }

            Set<String> tariffAddonRefs = importedTables(meta, "tariff_addons");
            assertTrue(tariffAddonRefs.contains("tariffs"), "tariff_addons missing FK to tariffs");
            assertTrue(tariffAddonRefs.contains("addons"), "tariff_addons missing FK to addons");

            // V2__addon_management: nullable allowance columns on addons.
            for (String column : new String[]{"data_mb", "voice_minutes", "sms_count"}) {
                assertTrue(columnExists(meta, "addons", column),
                        "addons column missing: " + column);
            }

            // V2__addon_management: seeded addon catalog (no tariff_addons seeds - V1 seeds no
            // tariffs, links are runtime-managed through POST /api/v1/addons).
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT count(*) FROM addons WHERE status = 'ACTIVE' AND code IN "
                                 + "('DATA_5GB', 'DATA_10GB', 'SMS_500', 'VOICE_300', 'VAS_CALLERTUNE')")) {
                assertTrue(rs.next());
                assertEquals(5, rs.getInt(1), "expected 5 seeded addons");
            }

            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT data_mb FROM addons WHERE code = 'DATA_5GB'")) {
                assertTrue(rs.next(), "seeded addon DATA_5GB missing");
                assertEquals(5120L, rs.getLong("data_mb"), "DATA_5GB data_mb allowance");
            }
        }
    }

    private static boolean tableExists(DatabaseMetaData meta, String table) throws Exception {
        try (ResultSet rs = meta.getTables(null, "public", table, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private static boolean columnExists(DatabaseMetaData meta, String table, String column)
            throws Exception {
        try (ResultSet rs = meta.getColumns(null, "public", table, column)) {
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
