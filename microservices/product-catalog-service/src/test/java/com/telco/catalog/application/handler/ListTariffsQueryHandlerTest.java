package com.telco.catalog.application.handler;

import com.telco.catalog.application.query.ListTariffsQuery;
import com.telco.catalog.domain.model.Tariff;
import com.telco.catalog.domain.model.TariffStatus;
import com.telco.catalog.domain.model.TariffType;
import com.telco.catalog.infrastructure.persistence.TariffRepository;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.common.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListTariffsQueryHandlerTest {

    @Mock private TariffRepository tariffRepository;

    private ListTariffsQueryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ListTariffsQueryHandler(tariffRepository);
    }

    @Test
    void returns_page_of_active_tariffs() {
        Tariff tariff = Tariff.create("PLAN-A", "Plan A", TariffType.POSTPAID,
                new BigDecimal("49.99"), "TRY", 100, 50, 1024, null,
                Instant.parse("2026-01-01T00:00:00Z"), null);
        tariff.activate();
        when(tariffRepository.findByStatusAndEffectiveFromBeforeAndEffectiveToIsNull(
                eq(TariffStatus.ACTIVE), any(), any()))
                .thenReturn(new PageImpl<>(List.of(tariff), PageRequest.of(0, 20), 1));

        PageResult<?> result = handler.handle(new ListTariffsQuery(0, 20, null));

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void returns_empty_page_when_no_active_tariffs() {
        when(tariffRepository.findByStatusAndEffectiveFromBeforeAndEffectiveToIsNull(
                any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        PageResult<?> result = handler.handle(new ListTariffsQuery(0, 20, null));

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    void absent_sort_defaults_to_created_at_desc() {
        when(tariffRepository.findByStatusAndEffectiveFromBeforeAndEffectiveToIsNull(
                any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        handler.handle(new ListTariffsQuery(0, 20, null));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(tariffRepository).findByStatusAndEffectiveFromBeforeAndEffectiveToIsNull(
                eq(TariffStatus.ACTIVE), any(), pageable.capture());
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Test
    void explicit_sort_is_applied_to_the_repository_call() {
        when(tariffRepository.findByStatusAndEffectiveFromBeforeAndEffectiveToIsNull(
                any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        handler.handle(new ListTariffsQuery(0, 20, "monthlyFee,asc"));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(tariffRepository).findByStatusAndEffectiveFromBeforeAndEffectiveToIsNull(
                eq(TariffStatus.ACTIVE), any(), pageable.capture());
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.ASC, "monthlyFee"));
    }

    @Test
    void unknown_sort_property_raises_validation_error() {
        assertThatThrownBy(() -> handler.handle(new ListTariffsQuery(0, 20, "targetSegment,asc")))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(tariffRepository);
    }
}
