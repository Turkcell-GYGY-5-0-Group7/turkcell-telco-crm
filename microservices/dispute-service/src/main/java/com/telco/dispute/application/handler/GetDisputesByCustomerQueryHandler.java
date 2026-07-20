package com.telco.dispute.application.handler;

import com.telco.dispute.application.dto.DisputeResponse;
import com.telco.dispute.application.query.GetDisputesByCustomerQuery;
import com.telco.dispute.domain.Dispute;
import com.telco.dispute.infrastructure.persistence.DisputeRepository;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.cqrs.QueryHandler;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Handles {@link GetDisputesByCustomerQuery}. A non-admin caller is always scoped to their own
 * {@code callerCustomerId}, regardless of the requested {@code customerId} - the "silently scope,
 * don't 403" style used by order-service's list endpoint, preventing IDOR by construction rather than
 * reject-and-leak-existence. {@code @Transactional(readOnly = true)} is load-bearing (see
 * {@link GetDisputeQueryHandler}'s javadoc).
 */
@Component
public class GetDisputesByCustomerQueryHandler
        implements QueryHandler<GetDisputesByCustomerQuery, PageResult<DisputeResponse>> {

    private final DisputeRepository disputeRepository;

    public GetDisputesByCustomerQueryHandler(DisputeRepository disputeRepository) {
        this.disputeRepository = disputeRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<DisputeResponse> handle(GetDisputesByCustomerQuery query) {
        PageRequest pageable = PageRequest.of(query.page(), query.size());

        UUID effectiveCustomerId = query.callerIsAdmin()
                ? query.customerId()
                : UUID.fromString(query.callerCustomerId());

        Page<DisputeResponse> page = disputeRepository.findByCustomerId(effectiveCustomerId, pageable)
                .map(DisputeResponse::from);

        return new PageResult<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
