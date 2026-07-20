package com.telco.dispute.application.handler;

import com.telco.dispute.application.dto.DisputeResponse;
import com.telco.dispute.application.query.GetDisputeQuery;
import com.telco.dispute.domain.Dispute;
import com.telco.dispute.infrastructure.persistence.DisputeRepository;
import com.telco.platform.common.exception.AccessDeniedException;
import com.telco.platform.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Note: {@link GetDisputeQueryHandler#handle} is annotated {@code @Transactional(readOnly = true)} -
 * load-bearing per {@code docs/tasks/lessons.md}'s 2026-07-06 entry, since {@link DisputeResponse#from}
 * touches lazy {@code @OneToMany} collections. A Mockito-mocked repository can never itself detect a
 * missing annotation (it never returns a real Hibernate lazy proxy) - this is exactly the blind spot
 * that lesson documents; the annotation's presence must be verified by code review, not this test.
 */
@ExtendWith(MockitoExtension.class)
class GetDisputeQueryHandlerTest {

    @Mock private DisputeRepository disputeRepository;

    private GetDisputeQueryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetDisputeQueryHandler(disputeRepository);
    }

    @Test
    void returns_dispute_for_owning_customer() {
        UUID customerId = UUID.randomUUID();
        Dispute dispute = Dispute.create(UUID.randomUUID(), null, customerId, "R", BigDecimal.TEN);
        when(disputeRepository.findById(dispute.getId())).thenReturn(Optional.of(dispute));

        DisputeResponse response = handler.handle(
                new GetDisputeQuery(dispute.getId(), customerId.toString(), false));

        assertThat(response.id()).isEqualTo(dispute.getId());
        assertThat(response.customerId()).isEqualTo(customerId);
    }

    @Test
    void admin_may_read_any_dispute() {
        Dispute dispute = Dispute.create(UUID.randomUUID(), null, UUID.randomUUID(), "R", BigDecimal.TEN);
        when(disputeRepository.findById(dispute.getId())).thenReturn(Optional.of(dispute));

        DisputeResponse response = handler.handle(
                new GetDisputeQuery(dispute.getId(), UUID.randomUUID().toString(), true));

        assertThat(response.id()).isEqualTo(dispute.getId());
    }

    @Test
    void throws_access_denied_when_dispute_belongs_to_a_different_customer() {
        Dispute dispute = Dispute.create(UUID.randomUUID(), null, UUID.randomUUID(), "R", BigDecimal.TEN);
        when(disputeRepository.findById(dispute.getId())).thenReturn(Optional.of(dispute));

        assertThatThrownBy(() -> handler.handle(
                new GetDisputeQuery(dispute.getId(), UUID.randomUUID().toString(), false)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void throws_not_found_when_dispute_does_not_exist() {
        UUID disputeId = UUID.randomUUID();
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(
                new GetDisputeQuery(disputeId, UUID.randomUUID().toString(), false)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
