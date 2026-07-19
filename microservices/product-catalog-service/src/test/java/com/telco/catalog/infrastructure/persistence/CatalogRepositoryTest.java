package com.telco.catalog.infrastructure.persistence;

import com.telco.catalog.domain.model.Addon;
import com.telco.catalog.domain.model.AddonType;
import com.telco.catalog.domain.model.Tariff;
import com.telco.catalog.domain.model.TariffStatus;
import com.telco.catalog.domain.model.TariffType;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CatalogRepositoryTest {

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
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.cloud.config.enabled", () -> "false");
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cache.type", () -> "none");
    }

    @Autowired private TariffRepository tariffRepository;
    @Autowired private TariffVersionRepository tariffVersionRepository;
    @Autowired private AddonRepository addonRepository;
    @Autowired private TestEntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void findByCode_returns_tariff_and_existsByCode_matches() {
        Tariff tariff = saveTariff("CODE-A", TariffStatus.DRAFT);
        flushAndClear();

        assertThat(tariffRepository.findByCode("CODE-A")).isPresent()
                .get().extracting(Tariff::getId).isEqualTo(tariff.getId());
        assertThat(tariffRepository.existsByCode("CODE-A")).isTrue();
        assertThat(tariffRepository.existsByCode("MISSING")).isFalse();
    }

    @Test
    void findByStatusAndEffectiveFromBeforeAndEffectiveToIsNull_returns_open_ended_active_tariffs() {
        Tariff active = saveTariff("ACTIVE-1", TariffStatus.DRAFT);
        active.activate();
        tariffRepository.save(active);

        Tariff draft = saveTariff("DRAFT-1", TariffStatus.DRAFT);

        flushAndClear();

        Instant past = Instant.parse("2030-01-01T00:00:00Z");
        var page = tariffRepository.findByStatusAndEffectiveFromBeforeAndEffectiveToIsNull(
                TariffStatus.ACTIVE, past, org.springframework.data.domain.PageRequest.of(0, 10));

        List<String> codes = page.map(Tariff::getCode).toList();
        assertThat(codes).contains("ACTIVE-1");
        assertThat(codes).doesNotContain("DRAFT-1");
    }

    @Test
    void addonRepository_findByTariffs_Code_returns_addons_linked_to_tariff() {
        Tariff tariff = saveTariff("PLAN-WITH-ADDON", TariffStatus.DRAFT);
        flushAndClear();

        UUID addonId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO addons (id, code, name, price, currency, type, validity_days, status, created_at) "
                        + "VALUES (?, 'DATA-5GB', 'Data 5GB', 15.00, 'TRY', 'DATA', 30, 'ACTIVE', now())",
                addonId);
        jdbcTemplate.update(
                "INSERT INTO tariff_addons (tariff_id, addon_id) VALUES (?, ?)",
                tariff.getId(), addonId);
        flushAndClear();

        var result = addonRepository.findByTariffs_Code(
                "PLAN-WITH-ADDON", org.springframework.data.domain.PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCode()).isEqualTo("DATA-5GB");
    }

    @Test
    void tariff_addAddon_persists_join_rows_through_owning_side() {
        Tariff tariff = saveTariff("PLAN-OWNING-SIDE", TariffStatus.DRAFT);
        Addon addon = Addon.create("LINK_DATA_1GB", "Data 1 GB", new BigDecimal("15.00"), "TRY",
                AddonType.DATA, 30, 1024L, null, null);
        addonRepository.save(addon);

        tariff.addAddon(addon);
        tariffRepository.save(tariff);
        flushAndClear();

        Integer joinRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM tariff_addons WHERE tariff_id = ? AND addon_id = ?",
                Integer.class, tariff.getId(), addon.getId());
        assertThat(joinRows).isEqualTo(1);

        var linked = addonRepository.findByTariffs_Code(
                "PLAN-OWNING-SIDE", org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(linked.getContent()).hasSize(1);
        assertThat(linked.getContent().get(0).getCode()).isEqualTo("LINK_DATA_1GB");
        assertThat(linked.getContent().get(0).getDataMb()).isEqualTo(1024L);
    }

    @Test
    void migration_seeded_addons_are_readable_with_allowances() {
        assertThat(addonRepository.findByCode("DATA_5GB")).hasValueSatisfying(addon -> {
            assertThat(addon.getType()).isEqualTo(AddonType.DATA);
            assertThat(addon.getDataMb()).isEqualTo(5120L);
            assertThat(addon.getVoiceMinutes()).isNull();
            assertThat(addon.getSmsCount()).isNull();
            assertThat(addon.getStatus()).isEqualTo("ACTIVE");
        });
        assertThat(addonRepository.findByCode("VOICE_300")).hasValueSatisfying(addon ->
                assertThat(addon.getVoiceMinutes()).isEqualTo(300L));
        assertThat(addonRepository.findByCode("SMS_500")).hasValueSatisfying(addon ->
                assertThat(addon.getSmsCount()).isEqualTo(500L));
    }

    private Tariff saveTariff(String code, TariffStatus initialStatus) {
        Tariff tariff = Tariff.create(code, code + " Name", TariffType.POSTPAID,
                new BigDecimal("49.99"), "TRY", 100, 50, 1024, null,
                Instant.parse("2026-01-01T00:00:00Z"), null);
        return tariffRepository.save(tariff);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
