package com.telco.subscription.application.handler;

import com.telco.subscription.application.AuditLogWriter;
import com.telco.subscription.application.command.ExpireMsisdnReservationsCommand;
import com.telco.subscription.application.command.ExpireMsisdnReservationsResult;
import com.telco.subscription.domain.MsisdnPool;
import com.telco.subscription.domain.MsisdnStatus;
import com.telco.subscription.infrastructure.MsisdnPoolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpireMsisdnReservationsCommandHandlerTest {

    @Mock
    private MsisdnPoolRepository msisdnPoolRepository;
    @Mock
    private AuditLogWriter auditLogWriter;

    private ExpireMsisdnReservationsCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ExpireMsisdnReservationsCommandHandler(msisdnPoolRepository, auditLogWriter);
    }

    @Test
    void releasesEveryExpiredReservationThroughTheDomainMethodAndAudits() {
        MsisdnPool expiredOne = reservedPool("905550000001", Instant.now().minus(1, ChronoUnit.HOURS));
        MsisdnPool expiredTwo = reservedPool("905550000002", Instant.now().minus(2, ChronoUnit.HOURS));
        when(msisdnPoolRepository.findByStatusAndReservedUntilBefore(eq(MsisdnStatus.RESERVED), any(Instant.class)))
                .thenReturn(List.of(expiredOne, expiredTwo));

        ExpireMsisdnReservationsResult result = handler.handle(new ExpireMsisdnReservationsCommand());

        assertThat(result.releasedCount()).isEqualTo(2);
        assertThat(expiredOne.getStatus()).isEqualTo(MsisdnStatus.FREE);
        assertThat(expiredOne.getReservedUntil()).isNull();
        assertThat(expiredTwo.getStatus()).isEqualTo(MsisdnStatus.FREE);
        verify(msisdnPoolRepository).save(expiredOne);
        verify(msisdnPoolRepository).save(expiredTwo);

        ArgumentCaptor<String> entityIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogWriter, times(2)).log(
                eq("MSISDN_RESERVATION_EXPIRED"), eq("MsisdnPool"), entityIdCaptor.capture(), any(Map.class));
        assertThat(entityIdCaptor.getAllValues()).containsExactlyInAnyOrder("905550000001", "905550000002");
    }

    @Test
    void doesNothingWhenNoReservationHasExpired() {
        when(msisdnPoolRepository.findByStatusAndReservedUntilBefore(eq(MsisdnStatus.RESERVED), any(Instant.class)))
                .thenReturn(List.of());

        ExpireMsisdnReservationsResult result = handler.handle(new ExpireMsisdnReservationsCommand());

        assertThat(result.releasedCount()).isZero();
        verify(msisdnPoolRepository, never()).save(any());
        verify(auditLogWriter, never()).log(any(), any(), any(), any());
    }

    // MsisdnPool has no public constructor or factory that yields a RESERVED instance directly (its
    // no-arg constructor is `protected ... for JPA only`, and `reserve(Instant)` requires an already-
    // FREE instance to start from) - reflection is the only way to build a RESERVED fixture here.
    private static MsisdnPool reservedPool(String msisdn, Instant reservedUntil) {
        try {
            java.lang.reflect.Constructor<MsisdnPool> constructor = MsisdnPool.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            MsisdnPool pool = constructor.newInstance();
            setField(pool, "msisdn", msisdn);
            setField(pool, "status", MsisdnStatus.RESERVED);
            setField(pool, "reservedUntil", reservedUntil);
            return pool;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = MsisdnPool.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
