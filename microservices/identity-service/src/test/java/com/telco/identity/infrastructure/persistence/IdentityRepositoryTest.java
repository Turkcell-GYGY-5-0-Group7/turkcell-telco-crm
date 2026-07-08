package com.telco.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.telco.identity.domain.Permission;
import com.telco.identity.domain.Role;
import com.telco.identity.domain.User;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Slice test for the identity repositories against a real PostgreSQL container with the production
 * Flyway schema applied (feature 5.2.2, ADR-013). Verifies the keycloak_id / username / email lookups
 * and that the role/permission join-table mapping resolves a user's effective permissions end to end.
 *
 * <p>Flyway (not Hibernate) owns the schema, mirroring production {@code spring.flyway.locations} so
 * the platform outbox migration applies alongside the service schema; Hibernate runs in
 * {@code ddl-auto=none}. The schema is migrated before the context loads (the {@code @DataJpaTest}
 * slice does not auto-configure Flyway). Config-server and Eureka are disabled - the repositories are
 * the unit under test.
 *
 * <p>{@code @ActiveProfiles("test")} keeps this slice on the same logging profile as every other
 * Spring context in the module; without it, the context loads under the default profile, which wires
 * the Loki appender (logback-spring.xml) and leaves its async sender retrying against an unreachable
 * Loki after this test's context shuts down - poisoning the next context's startup with a spurious
 * "Logback configuration error detected" failure.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class IdentityRepositoryTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    static {
        POSTGRES.start();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/migration/platform")
                .load()
                .migrate();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.cloud.config.enabled", () -> "false");
        registry.add("eureka.client.enabled", () -> "false");
    }

    @Autowired
    private UserRepository users;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private PermissionRepository permissions;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void looksUpUsersByKeycloakIdUsernameAndEmail() {
        User saved = users.save(User.provision("kc-100", "ayse", "ayse@example.com"));
        flushAndClear();

        assertThat(users.findByKeycloakId("kc-100")).get()
                .extracting(User::getId).isEqualTo(saved.getId());
        assertThat(users.findByUsername("ayse")).get()
                .extracting(User::getId).isEqualTo(saved.getId());
        assertThat(users.findByEmail("ayse@example.com")).get()
                .extracting(User::getId).isEqualTo(saved.getId());
        assertThat(users.existsByKeycloakId("kc-100")).isTrue();
        assertThat(users.findByKeycloakId("missing")).isEmpty();
    }

    @Test
    void resolvesEffectivePermissionsThroughTheRoleAndPermissionJoinTables() {
        Permission read = permissions.save(Permission.of("user:read"));
        Permission write = permissions.save(Permission.of("user:write"));

        Role admin = Role.of("RBAC_TEST_ROLE");
        admin.addPermission(read);
        admin.addPermission(write);
        roles.save(admin);

        User user = User.provision("kc-200", "mehmet", "mehmet@example.com");
        user.assignRole(admin);
        User saved = users.save(user);
        flushAndClear();

        User reloaded = users.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.effectivePermissions()).containsExactlyInAnyOrder("user:read", "user:write");
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
