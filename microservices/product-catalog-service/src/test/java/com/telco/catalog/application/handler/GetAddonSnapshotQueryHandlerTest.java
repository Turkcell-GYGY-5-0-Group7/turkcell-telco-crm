package com.telco.catalog.application.handler;

import com.telco.catalog.application.dto.AddonSnapshotResponse;
import com.telco.catalog.application.query.GetAddonSnapshotQuery;
import com.telco.catalog.domain.model.Addon;
import com.telco.catalog.domain.model.AddonType;
import com.telco.catalog.infrastructure.persistence.AddonRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAddonSnapshotQueryHandlerTest {

    @Mock private AddonRepository addonRepository;

    private GetAddonSnapshotQueryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetAddonSnapshotQueryHandler(addonRepository);
    }

    @Test
    void returns_snapshot_for_active_addon() {
        Addon addon = Addon.create("DATA_5GB", "Data 5 GB", new BigDecimal("49.90"), "TRY",
                AddonType.DATA, 30, 5120L, null, null);
        when(addonRepository.findByCode("DATA_5GB")).thenReturn(Optional.of(addon));

        AddonSnapshotResponse snapshot = handler.handle(new GetAddonSnapshotQuery("DATA_5GB"));

        assertThat(snapshot.code()).isEqualTo("DATA_5GB");
        assertThat(snapshot.type()).isEqualTo("DATA");
        assertThat(snapshot.price()).isEqualByComparingTo("49.90");
        assertThat(snapshot.currency()).isEqualTo("TRY");
        assertThat(snapshot.validityDays()).isEqualTo(30);
        assertThat(snapshot.dataMb()).isEqualTo(5120L);
        assertThat(snapshot.voiceMinutes()).isNull();
        assertThat(snapshot.smsCount()).isNull();
    }

    @Test
    void throws_not_found_when_code_missing() {
        when(addonRepository.findByCode("NONE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new GetAddonSnapshotQuery("NONE")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void throws_not_found_when_addon_is_not_active() {
        Addon addon = Addon.create("RETIRED_1", "Retired", new BigDecimal("9.90"), "TRY",
                AddonType.VAS, 30, null, null, null);
        // Addon is immutable with no status transition API yet; force the persisted-state shape.
        ReflectionTestUtils.setField(addon, "status", "RETIRED");
        when(addonRepository.findByCode("RETIRED_1")).thenReturn(Optional.of(addon));

        assertThatThrownBy(() -> handler.handle(new GetAddonSnapshotQuery("RETIRED_1")))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
