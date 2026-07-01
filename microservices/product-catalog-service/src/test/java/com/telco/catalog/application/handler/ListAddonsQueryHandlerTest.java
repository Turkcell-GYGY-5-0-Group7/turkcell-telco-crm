package com.telco.catalog.application.handler;

import com.telco.catalog.application.query.ListAddonsQuery;
import com.telco.catalog.infrastructure.persistence.AddonRepository;
import com.telco.platform.common.api.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListAddonsQueryHandlerTest {

    @Mock private AddonRepository addonRepository;

    private ListAddonsQueryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ListAddonsQueryHandler(addonRepository);
    }

    @Test
    void returns_all_addons_when_no_tariff_code_filter() {
        when(addonRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        PageResult<?> result = handler.handle(new ListAddonsQuery(null, 0, 20));

        assertThat(result.content()).isEmpty();
        verify(addonRepository).findAll(any(PageRequest.class));
    }

    @Test
    void filters_by_tariff_code_when_provided() {
        when(addonRepository.findByTariffs_Code(eq("PLAN-A"), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        PageResult<?> result = handler.handle(new ListAddonsQuery("PLAN-A", 0, 20));

        assertThat(result.content()).isEmpty();
        verify(addonRepository).findByTariffs_Code(eq("PLAN-A"), any());
    }

    @Test
    void treats_blank_tariff_code_as_no_filter() {
        when(addonRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        handler.handle(new ListAddonsQuery("  ", 0, 20));

        verify(addonRepository).findAll(any(PageRequest.class));
    }
}
