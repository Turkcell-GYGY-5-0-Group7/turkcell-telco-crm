package com.telco.dispute.application.handler;

import com.telco.dispute.application.query.GetDisputeEvidenceDownloadUrlQuery;
import com.telco.dispute.domain.Dispute;
import com.telco.dispute.infrastructure.persistence.DisputeRepository;
import com.telco.dispute.infrastructure.storage.DisputeEvidenceStorage;
import com.telco.platform.common.exception.AccessDeniedException;
import com.telco.platform.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetDisputeEvidenceDownloadUrlQueryHandlerTest {

    @Mock private DisputeRepository disputeRepository;
    @Mock private DisputeEvidenceStorage disputeEvidenceStorage;

    private GetDisputeEvidenceDownloadUrlQueryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetDisputeEvidenceDownloadUrlQueryHandler(disputeRepository, disputeEvidenceStorage);
    }

    @Test
    void returns_presigned_url_for_owning_customer() {
        UUID customerId = UUID.randomUUID();
        Dispute dispute = Dispute.create(UUID.randomUUID(), null, customerId, "R", BigDecimal.TEN);
        dispute.addEvidence("customer-1", "d-1/receipt.pdf");
        UUID evidenceId = dispute.getEvidence().get(0).getId();
        when(disputeRepository.findById(dispute.getId())).thenReturn(Optional.of(dispute));
        when(disputeEvidenceStorage.presignedGetUrl(eq("d-1/receipt.pdf"), any(Duration.class)))
                .thenReturn("https://minio.local/presigned");

        String url = handler.handle(new GetDisputeEvidenceDownloadUrlQuery(
                dispute.getId(), evidenceId, customerId.toString(), false));

        assertThat(url).isEqualTo("https://minio.local/presigned");
    }

    @Test
    void throws_not_found_when_evidence_does_not_exist_on_the_dispute() {
        UUID customerId = UUID.randomUUID();
        Dispute dispute = Dispute.create(UUID.randomUUID(), null, customerId, "R", BigDecimal.TEN);
        when(disputeRepository.findById(dispute.getId())).thenReturn(Optional.of(dispute));

        assertThatThrownBy(() -> handler.handle(new GetDisputeEvidenceDownloadUrlQuery(
                dispute.getId(), UUID.randomUUID(), customerId.toString(), false)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void throws_access_denied_when_dispute_belongs_to_a_different_customer() {
        Dispute dispute = Dispute.create(UUID.randomUUID(), null, UUID.randomUUID(), "R", BigDecimal.TEN);
        dispute.addEvidence("customer-1", "d-1/receipt.pdf");
        UUID evidenceId = dispute.getEvidence().get(0).getId();
        when(disputeRepository.findById(dispute.getId())).thenReturn(Optional.of(dispute));

        assertThatThrownBy(() -> handler.handle(new GetDisputeEvidenceDownloadUrlQuery(
                dispute.getId(), evidenceId, UUID.randomUUID().toString(), false)))
                .isInstanceOf(AccessDeniedException.class);
    }
}
