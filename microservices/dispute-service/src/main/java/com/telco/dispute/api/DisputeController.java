package com.telco.dispute.api;

import com.telco.dispute.application.command.OpenDisputeCommand;
import com.telco.dispute.application.command.ResolveDisputeCustomerCommand;
import com.telco.dispute.application.command.ResolveDisputeMerchantCommand;
import com.telco.dispute.application.command.SubmitEvidenceCommand;
import com.telco.dispute.application.command.WithdrawDisputeCommand;
import com.telco.dispute.application.dto.DisputeResponse;
import com.telco.dispute.application.dto.OpenDisputeRequest;
import com.telco.dispute.application.dto.ResolveDisputeRequest;
import com.telco.dispute.application.query.GetDisputeEvidenceDownloadUrlQuery;
import com.telco.dispute.application.query.GetDisputeQuery;
import com.telco.dispute.application.query.GetDisputesByCustomerQuery;
import com.telco.dispute.infrastructure.storage.DisputeEvidenceStorage;
import com.telco.platform.common.api.ApiResult;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.common.context.CurrentUserProvider;
import com.telco.platform.common.exception.ValidationException;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.starter.api.ApiResponseFactory;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Dispute lifecycle API (open, submit evidence, resolve, withdraw, read) - thin edge: HTTP -&gt;
 * command/query via {@link Mediator} -&gt; {@link ApiResult} (ADR-008, ADR-015).
 *
 * <p>Ownership: a {@code SUBSCRIBER} may act on their own dispute only, enforced inside each
 * handler by comparing {@link CurrentUserProvider#currentUser()}'s {@code customerId} (the caller's
 * own linked customer-service id) against the loaded {@code Dispute.customerId} - never the raw
 * Keycloak subject. Resolution is agent/admin-only ({@code ADMIN} or {@code CALL_CENTER_AGENT} - the
 * real realm role per {@code docs/architecture/keycloak-and-auth.md}, not ticket-service's
 * nonexistent {@code SUPPORT}), with no ownership check - any agent may resolve any dispute.
 *
 * <p>The evidence-upload endpoint stores the file to MinIO directly in this controller (not inside a
 * command handler, unlike customer-service's KYC upload) so a single HTTP call can both upload and
 * record the evidence row - one of the two shapes 22.3.2's task spec explicitly permits ("fold both
 * into a single multipart endpoint if simpler").
 */
@RestController
@RequestMapping("/api/v1/disputes")
public class DisputeController {

    private final Mediator mediator;
    private final ApiResponseFactory responses;
    private final CurrentUserProvider currentUserProvider;
    private final DisputeEvidenceStorage disputeEvidenceStorage;

    public DisputeController(Mediator mediator, ApiResponseFactory responses,
                             CurrentUserProvider currentUserProvider,
                             DisputeEvidenceStorage disputeEvidenceStorage) {
        this.mediator = mediator;
        this.responses = responses;
        this.currentUserProvider = currentUserProvider;
        this.disputeEvidenceStorage = disputeEvidenceStorage;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUBSCRIBER') or hasRole('ADMIN')")
    public ApiResult<UUID> openDispute(@Valid @RequestBody OpenDisputeRequest request) {
        boolean isAdmin = currentUserProvider.currentUser().hasRole("ADMIN");
        String callerCustomerId = currentUserProvider.currentUser().customerId();

        UUID disputeId = mediator.send(new OpenDisputeCommand(
                request.invoiceId(), request.paymentId(), request.customerId(),
                request.reasonCode(), request.disputedAmount(), callerCustomerId, isAdmin));
        return responses.ok(disputeId);
    }

    @PostMapping("/{id}/evidence/upload")
    @PreAuthorize("hasRole('SUBSCRIBER') or hasRole('ADMIN')")
    public ApiResult<Void> uploadEvidence(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        boolean isAdmin = currentUserProvider.currentUser().hasRole("ADMIN");
        String callerCustomerId = currentUserProvider.currentUser().customerId();
        String actor = currentUserProvider.currentUser().userId();

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new ValidationException("could not read uploaded file", Map.of());
        }

        String objectKey = id + "/" + UUID.randomUUID() + "-" + file.getOriginalFilename();
        String objectRef = disputeEvidenceStorage.store(objectKey, content, file.getContentType());

        mediator.send(new SubmitEvidenceCommand(id, actor, objectRef, callerCustomerId, isAdmin));
        return responses.ok(null);
    }

    @GetMapping("/{id}/evidence/{evidenceId}/download-url")
    @PreAuthorize("hasRole('SUBSCRIBER') or hasRole('ADMIN')")
    public ApiResult<String> getEvidenceDownloadUrl(@PathVariable UUID id, @PathVariable UUID evidenceId) {
        boolean isAdmin = currentUserProvider.currentUser().hasRole("ADMIN");
        String callerCustomerId = currentUserProvider.currentUser().customerId();

        return responses.ok(mediator.query(
                new GetDisputeEvidenceDownloadUrlQuery(id, evidenceId, callerCustomerId, isAdmin)));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CALL_CENTER_AGENT')")
    public ApiResult<Void> resolveDispute(@PathVariable UUID id, @Valid @RequestBody ResolveDisputeRequest request) {
        String actor = currentUserProvider.currentUser().userId();

        if (request.outcome() == ResolveDisputeRequest.Outcome.CUSTOMER) {
            if (request.resolutionAmount() == null) {
                throw new ValidationException("resolutionAmount is required for a CUSTOMER outcome", Map.of());
            }
            mediator.send(new ResolveDisputeCustomerCommand(id, request.resolutionAmount(), actor));
        } else {
            mediator.send(new ResolveDisputeMerchantCommand(id, actor));
        }
        return responses.ok(null);
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasRole('SUBSCRIBER') or hasRole('ADMIN')")
    public ApiResult<Void> withdrawDispute(@PathVariable UUID id) {
        boolean isAdmin = currentUserProvider.currentUser().hasRole("ADMIN");
        String callerCustomerId = currentUserProvider.currentUser().customerId();
        String actor = currentUserProvider.currentUser().userId();

        mediator.send(new WithdrawDisputeCommand(id, actor, callerCustomerId, isAdmin));
        return responses.ok(null);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUBSCRIBER') or hasRole('ADMIN')")
    public ApiResult<DisputeResponse> getDispute(@PathVariable UUID id) {
        boolean isAdmin = currentUserProvider.currentUser().hasRole("ADMIN");
        String callerCustomerId = currentUserProvider.currentUser().customerId();

        return responses.ok(mediator.query(new GetDisputeQuery(id, callerCustomerId, isAdmin)));
    }

    @GetMapping
    @PreAuthorize("hasRole('SUBSCRIBER') or hasRole('ADMIN')")
    public ApiResult<PageResult<DisputeResponse>> getDisputesByCustomer(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        boolean isAdmin = currentUserProvider.currentUser().hasRole("ADMIN");
        String callerCustomerId = currentUserProvider.currentUser().customerId();

        return responses.ok(mediator.query(
                new GetDisputesByCustomerQuery(customerId, page, size, callerCustomerId, isAdmin)));
    }
}
