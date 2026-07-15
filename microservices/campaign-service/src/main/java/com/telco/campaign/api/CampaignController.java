package com.telco.campaign.api;

import com.telco.campaign.application.command.ActivateCampaignCommand;
import com.telco.campaign.application.command.CancelCampaignCommand;
import com.telco.campaign.application.command.CreateCampaignCommand;
import com.telco.campaign.application.command.PauseCampaignCommand;
import com.telco.campaign.application.dto.CampaignResponse;
import com.telco.campaign.application.dto.CreateCampaignRequest;
import com.telco.campaign.application.query.GetCampaignQuery;
import com.telco.campaign.application.query.ListCampaignsQuery;
import com.telco.platform.common.api.ApiResult;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.starter.api.ApiResponseFactory;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Minimal admin campaign API (Feature 21.2.1): the surface needed to create and administer a campaign
 * so 21.3's validate flow has real data to check against. Not a full campaign-marketing admin UI (out
 * of scope this sprint - see the 21.2.1 task description).
 *
 * <p>Thin edge: HTTP -&gt; command/query via {@link Mediator} -&gt; {@link ApiResult} (ADR-004,
 * ADR-008, ADR-015). No business logic here.
 */
@RestController
@RequestMapping("/api/v1/campaigns")
public class CampaignController {

    private final Mediator mediator;
    private final ApiResponseFactory responses;

    public CampaignController(Mediator mediator, ApiResponseFactory responses) {
        this.mediator = mediator;
        this.responses = responses;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<CampaignResponse> createCampaign(@Valid @RequestBody CreateCampaignRequest request) {
        CreateCampaignCommand command = new CreateCampaignCommand(
                request.code(),
                request.name(),
                request.description(),
                request.discountType(),
                request.discountValue(),
                request.applicableTariffCodes(),
                request.validFrom(),
                request.validTo(),
                request.totalRedemptionCap(),
                request.perCustomerRedemptionCap()
        );
        return responses.ok(mediator.send(command));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<CampaignResponse> activateCampaign(@PathVariable UUID id) {
        return responses.ok(mediator.send(new ActivateCampaignCommand(id)));
    }

    @PostMapping("/{id}/pause")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<CampaignResponse> pauseCampaign(@PathVariable UUID id) {
        return responses.ok(mediator.send(new PauseCampaignCommand(id)));
    }

    /** No hard delete: cancels the campaign (any non-terminal status -&gt; CANCELLED). */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<CampaignResponse> cancelCampaign(@PathVariable UUID id) {
        return responses.ok(mediator.send(new CancelCampaignCommand(id)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<CampaignResponse> getCampaign(@PathVariable UUID id) {
        return responses.ok(mediator.query(new GetCampaignQuery(id)));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<PageResult<CampaignResponse>> listCampaigns(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return responses.ok(mediator.query(new ListCampaignsQuery(page, size)));
    }
}
