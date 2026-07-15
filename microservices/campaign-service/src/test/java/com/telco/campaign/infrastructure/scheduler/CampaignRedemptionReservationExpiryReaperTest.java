package com.telco.campaign.infrastructure.scheduler;

import com.telco.campaign.application.command.ExpireCampaignRedemptionReservationsCommand;
import com.telco.campaign.application.command.ExpireCampaignRedemptionReservationsResult;
import com.telco.platform.common.exception.DependencyFailureException;
import com.telco.platform.lock.DistributedLock;
import com.telco.platform.lock.LockErrorCode;
import com.telco.platform.mediator.Mediator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignRedemptionReservationExpiryReaperTest {

    private static final long LOCK_LEASE_MS = 30_000;

    @Mock
    private Mediator mediator;
    @Mock
    private DistributedLock distributedLock;

    private CampaignRedemptionReservationExpiryReaper reaper;

    @BeforeEach
    void setUp() {
        reaper = new CampaignRedemptionReservationExpiryReaper(mediator, distributedLock, LOCK_LEASE_MS);
    }

    @Test
    @SuppressWarnings("unchecked")
    void tickAcquiresWithAnExplicitLeaseNotWatchdogManaged() {
        when(distributedLock.withLock(eq("campaign-service:redemption-reaper"),
                eq(Duration.ofMillis(LOCK_LEASE_MS)), any(Callable.class)))
                .thenAnswer(invocation -> {
                    Callable<Integer> action = invocation.getArgument(2);
                    return action.call();
                });
        when(mediator.send(any(ExpireCampaignRedemptionReservationsCommand.class)))
                .thenReturn(new ExpireCampaignRedemptionReservationsResult(3));

        int released = reaper.tick();

        assertThat(released).isEqualTo(3);
        ArgumentCaptor<Duration> leaseCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(distributedLock).withLock(eq("campaign-service:redemption-reaper"), leaseCaptor.capture(),
                any(Callable.class));
        assertThat(leaseCaptor.getValue()).isNotNull().isEqualTo(Duration.ofMillis(LOCK_LEASE_MS));
    }

    @Test
    @SuppressWarnings("unchecked")
    void tickReturnsNegativeOneWhenAnotherReplicaOwnsThisTickAndDoesNotPropagate() {
        when(distributedLock.withLock(any(String.class), any(Duration.class), any(Callable.class)))
                .thenThrow(new DependencyFailureException(
                        LockErrorCode.LOCK_ACQUISITION_FAILED, "contended", Map.of(), null));

        int released = reaper.tick();

        assertThat(released).isEqualTo(-1);
        verify(mediator, never()).send(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void tickPropagatesADifferentDependencyFailureRatherThanTreatingItAsLockContention() {
        DependencyFailureException otherFailure = new DependencyFailureException("redis down", null);
        when(distributedLock.withLock(any(String.class), any(Duration.class), any(Callable.class)))
                .thenThrow(otherFailure);

        assertThatThrownBy(() -> reaper.tick()).isSameAs(otherFailure);
    }
}
