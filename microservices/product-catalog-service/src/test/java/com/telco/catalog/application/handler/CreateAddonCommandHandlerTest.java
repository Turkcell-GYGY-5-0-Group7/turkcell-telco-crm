package com.telco.catalog.application.handler;

import com.telco.catalog.application.command.CreateAddonCommand;
import com.telco.catalog.application.dto.AddonResponse;
import com.telco.catalog.domain.model.Addon;
import com.telco.catalog.domain.model.AddonType;
import com.telco.catalog.domain.model.Tariff;
import com.telco.catalog.domain.model.TariffType;
import com.telco.catalog.infrastructure.persistence.AddonRepository;
import com.telco.catalog.infrastructure.persistence.TariffRepository;
import com.telco.platform.common.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateAddonCommandHandlerTest {

    @Mock private AddonRepository addonRepository;
    @Mock private TariffRepository tariffRepository;

    private CreateAddonCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CreateAddonCommandHandler(addonRepository, tariffRepository);
    }

    private static CreateAddonCommand command(Set<String> tariffCodes) {
        return new CreateAddonCommand(
                "DATA_5GB", "Data 5 GB", new BigDecimal("49.90"), "TRY",
                AddonType.DATA, 30, 5120L, null, null, tariffCodes);
    }

    private static Tariff tariff(String code) {
        Tariff tariff = Tariff.create(code, code + " Name", TariffType.POSTPAID,
                new BigDecimal("49.99"), "TRY", 100, 50, 1024, null,
                Instant.parse("2026-01-01T00:00:00Z"), null);
        tariff.activate();
        return tariff;
    }

    @Test
    void creates_addon_and_links_it_through_owning_tariff_side() {
        Tariff tariff = tariff("PLAN-A");
        when(addonRepository.existsByCode("DATA_5GB")).thenReturn(false);
        when(addonRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tariffRepository.findByCode("PLAN-A")).thenReturn(Optional.of(tariff));

        AddonResponse response = handler.handle(command(Set.of("PLAN-A")));

        assertThat(response.code()).isEqualTo("DATA_5GB");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.dataMb()).isEqualTo(5120L);
        assertThat(response.voiceMinutes()).isNull();
        assertThat(response.smsCount()).isNull();

        ArgumentCaptor<Addon> savedAddon = ArgumentCaptor.forClass(Addon.class);
        verify(addonRepository).save(savedAddon.capture());
        // The join table is owned by Tariff.addons: the addon must be attached to the tariff and
        // the tariff saved, otherwise no tariff_addons row would ever persist.
        assertThat(tariff.getAddons()).containsExactly(savedAddon.getValue());
        verify(tariffRepository).save(tariff);
    }

    @Test
    void creates_addon_without_links_when_no_tariff_codes_given() {
        when(addonRepository.existsByCode("DATA_5GB")).thenReturn(false);
        when(addonRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AddonResponse response = handler.handle(command(Set.of()));

        assertThat(response.code()).isEqualTo("DATA_5GB");
        verify(tariffRepository, never()).save(any());
    }

    @Test
    void rejects_duplicate_addon_code() {
        when(addonRepository.existsByCode("DATA_5GB")).thenReturn(true);

        assertThatThrownBy(() -> handler.handle(command(Set.of())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already exists");
        verify(addonRepository, never()).save(any());
    }

    @Test
    void rejects_unknown_tariff_code() {
        when(addonRepository.existsByCode("DATA_5GB")).thenReturn(false);
        when(addonRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tariffRepository.findByCode("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(command(Set.of("MISSING"))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("MISSING");
        verify(tariffRepository, never()).save(any());
    }
}
