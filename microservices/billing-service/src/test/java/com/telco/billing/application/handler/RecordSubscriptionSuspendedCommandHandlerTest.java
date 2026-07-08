package com.telco.billing.application.handler;

import com.telco.billing.application.command.RecordSubscriptionSuspendedCommand;
import com.telco.billing.infrastructure.entity.SubscriberBillingRecord;
import com.telco.billing.infrastructure.persistence.SubscriberBillingRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordSubscriptionSuspendedCommandHandlerTest {

    @Mock private SubscriberBillingRecordRepository subscriberRepo;

    private RecordSubscriptionSuspendedCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RecordSubscriptionSuspendedCommandHandler(subscriberRepo);
    }

    @Test
    void suspends_the_matching_billing_record() {
        UUID subscriptionId = UUID.randomUUID();
        Instant suspendedAt = Instant.now();
        SubscriberBillingRecord record = SubscriberBillingRecord.activated(
                subscriptionId, UUID.randomUUID(), "POSTPAID-M", Instant.now().minusSeconds(3600));
        when(subscriberRepo.findBySubscriptionId(subscriptionId)).thenReturn(Optional.of(record));

        handler.handle(new RecordSubscriptionSuspendedCommand(subscriptionId, suspendedAt));

        ArgumentCaptor<SubscriberBillingRecord> captor = ArgumentCaptor.forClass(SubscriberBillingRecord.class);
        verify(subscriberRepo).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(SubscriberBillingRecord.SUSPENDED);
        assertThat(captor.getValue().getSuspendedAt()).isEqualTo(suspendedAt);
    }

    @Test
    void is_a_no_op_when_no_billing_record_exists_for_the_subscription() {
        UUID subscriptionId = UUID.randomUUID();
        when(subscriberRepo.findBySubscriptionId(subscriptionId)).thenReturn(Optional.empty());

        handler.handle(new RecordSubscriptionSuspendedCommand(subscriptionId, Instant.now()));

        verify(subscriberRepo, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
