package com.telco.billing.application.handler;

import com.telco.billing.application.command.RunBillCommand;
import com.telco.billing.application.command.RunBillResult;
import com.telco.billing.infrastructure.entity.SubscriberBillingRecord;
import com.telco.billing.infrastructure.persistence.SubscriberBillingRecordRepository;
import com.telco.platform.common.exception.DependencyFailureException;
import com.telco.platform.lock.DistributedLock;
import com.telco.platform.lock.LockErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RunBillCommandHandlerTest {

    @Mock
    private SubscriberBillingRecordRepository subscriberRepo;
    @Mock
    private BillRunBatchProcessor batchProcessor;
    @Mock
    private DistributedLock distributedLock;

    private RunBillCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RunBillCommandHandler(subscriberRepo, batchProcessor, distributedLock, 500, 8);
    }

    @Test
    @SuppressWarnings("unchecked")
    void lockKeyIsScopedToTheBillingPeriodSoDifferentPeriodsDoNotBlockEachOther() throws Exception {
        when(distributedLock.withLock(any(String.class), isNull(), any(Callable.class)))
                .thenAnswer(invocation -> {
                    Callable<RunBillResult> action = invocation.getArgument(2);
                    return action.call();
                });
        when(subscriberRepo.findByStatus(SubscriberBillingRecord.ACTIVE)).thenReturn(List.of());

        Instant periodAStart = Instant.parse("2026-01-01T00:00:00Z");
        Instant periodAEnd = Instant.parse("2026-02-01T00:00:00Z");
        Instant periodBStart = Instant.parse("2026-02-01T00:00:00Z");
        Instant periodBEnd = Instant.parse("2026-03-01T00:00:00Z");

        handler.handle(new RunBillCommand(periodAStart, periodAEnd));
        handler.handle(new RunBillCommand(periodBStart, periodBEnd));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(distributedLock, times(2)).withLock(keyCaptor.capture(), isNull(), any(Callable.class));
        List<String> keys = keyCaptor.getAllValues();
        assertThat(keys.get(0)).isNotEqualTo(keys.get(1));
        assertThat(keys.get(0)).contains(periodAStart.toString(), periodAEnd.toString());
        assertThat(keys.get(1)).contains(periodBStart.toString(), periodBEnd.toString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void usesAWatchdogManagedLeaseNotAnExplicitOne() throws Exception {
        when(distributedLock.withLock(any(String.class), isNull(), any(Callable.class)))
                .thenAnswer(invocation -> {
                    Callable<RunBillResult> action = invocation.getArgument(2);
                    return action.call();
                });
        when(subscriberRepo.findByStatus(SubscriberBillingRecord.ACTIVE)).thenReturn(List.of());

        handler.handle(new RunBillCommand(Instant.now(), Instant.now().plusSeconds(1)));

        // leaseTime == null is exactly the watchdog-managed mode (ADR-024 Section 4); a bill-run's
        // duration is variable (measured 6m20s@100K, Sprint 14.3.2), the wrong case for a hard,
        // explicit-Duration lease that could expire mid-run.
        verify(distributedLock).withLock(any(String.class), isNull(), any(Callable.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void whenAnotherPodOwnsTheRunTheLoserNeverPartitionsOrDispatchesAnyBatches() {
        when(distributedLock.withLock(any(String.class), isNull(), any(Callable.class)))
                .thenThrow(new DependencyFailureException(
                        LockErrorCode.LOCK_ACQUISITION_FAILED, "contended", Map.of(), null));

        RunBillResult result = handler.handle(new RunBillCommand(Instant.now(), Instant.now().plusSeconds(1)));

        assertThat(result.runAlreadyOwned()).isTrue();
        assertThat(result.invoicesGenerated()).isZero();
        assertThat(result.invoicesSkipped()).isZero();
        // Directly proves the AC's "not just absence of duplicate invoice rows" requirement: the
        // loser never even reaches the subscriber query, let alone partitions or dispatches a batch.
        verify(subscriberRepo, never()).findByStatus(any());
        verify(batchProcessor, never()).processBatch(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void aDifferentDependencyFailurePropagatesRatherThanBeingTreatedAsLockContention() {
        DependencyFailureException otherFailure = new DependencyFailureException("db down", null);
        when(distributedLock.withLock(any(String.class), isNull(), any(Callable.class)))
                .thenThrow(otherFailure);

        assertThatThrownBy(() -> handler.handle(new RunBillCommand(Instant.now(), Instant.now().plusSeconds(1))))
                .isSameAs(otherFailure);
    }
}
