package com.telco.catalog.application.handler;

import com.telco.catalog.application.command.CreateAddonCommand;
import com.telco.catalog.application.dto.AddonResponse;
import com.telco.catalog.domain.model.Addon;
import com.telco.catalog.domain.model.AddonType;
import com.telco.catalog.infrastructure.persistence.AddonRepository;
import com.telco.platform.common.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateAddonCommandHandlerTest {

    @Mock private AddonRepository addonRepository;

    private CreateAddonCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CreateAddonCommandHandler(addonRepository);
    }

    private static CreateAddonCommand command() {
        return new CreateAddonCommand(
                "DATA_5GB", "Data 5 GB", new BigDecimal("49.90"), "TRY",
                AddonType.DATA, 30, 5120L, null, null);
    }

    @Test
    void creates_addon_with_allowance_fields() {
        when(addonRepository.existsByCode("DATA_5GB")).thenReturn(false);
        when(addonRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AddonResponse response = handler.handle(command());

        assertThat(response.code()).isEqualTo("DATA_5GB");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.dataMb()).isEqualTo(5120L);
        assertThat(response.voiceMinutes()).isNull();
        assertThat(response.smsCount()).isNull();

        ArgumentCaptor<Addon> savedAddon = ArgumentCaptor.forClass(Addon.class);
        verify(addonRepository).save(savedAddon.capture());
        assertThat(savedAddon.getValue().getDataMb()).isEqualTo(5120L);
    }

    @Test
    void rejects_duplicate_addon_code() {
        when(addonRepository.existsByCode("DATA_5GB")).thenReturn(true);

        assertThatThrownBy(() -> handler.handle(command()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already exists");
        verify(addonRepository, never()).save(any());
    }
}
