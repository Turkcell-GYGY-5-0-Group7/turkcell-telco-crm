package com.telco.dispute.application.handler;

import com.telco.dispute.application.dto.DisputeResponse;
import com.telco.dispute.application.query.GetDisputesByCustomerQuery;
import com.telco.dispute.domain.Dispute;
import com.telco.dispute.infrastructure.persistence.DisputeRepository;
import com.telco.platform.common.api.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetDisputesByCustomerQueryHandlerTest {

    @Mock private DisputeRepository disputeRepository;

    private GetDisputesByCustomerQueryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetDisputesByCustomerQueryHandler(disputeRepository);
    }

    @Test
    void non_admin_caller_is_scoped_to_their_own_customer_id_ignoring_requested_customerId() {
        UUID callerCustomerId = UUID.randomUUID();
        UUID requestedCustomerId = UUID.randomUUID();
        Dispute dispute = Dispute.create(UUID.randomUUID(), null, callerCustomerId, "R", BigDecimal.TEN);
        Page<Dispute> page = new PageImpl<>(List.of(dispute));
        when(disputeRepository.findByCustomerId(eq(callerCustomerId), any(Pageable.class))).thenReturn(page);

        PageResult<DisputeResponse> result = handler.handle(new GetDisputesByCustomerQuery(
                requestedCustomerId, 0, 20, callerCustomerId.toString(), false));

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).customerId()).isEqualTo(callerCustomerId);
        // The requested (mismatched) customerId is never queried for a non-admin caller.
        verify(disputeRepository, org.mockito.Mockito.never())
                .findByCustomerId(eq(requestedCustomerId), any(Pageable.class));
    }

    @Test
    void admin_caller_is_scoped_to_the_requested_customerId() {
        UUID requestedCustomerId = UUID.randomUUID();
        Dispute dispute = Dispute.create(UUID.randomUUID(), null, requestedCustomerId, "R", BigDecimal.TEN);
        Page<Dispute> page = new PageImpl<>(List.of(dispute));
        when(disputeRepository.findByCustomerId(eq(requestedCustomerId), any(Pageable.class))).thenReturn(page);

        PageResult<DisputeResponse> result = handler.handle(new GetDisputesByCustomerQuery(
                requestedCustomerId, 0, 20, UUID.randomUUID().toString(), true));

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
    }
}
