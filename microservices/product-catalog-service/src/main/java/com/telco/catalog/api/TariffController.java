package com.telco.catalog.api;

import com.telco.catalog.application.command.ChangeTariffPriceCommand;
import com.telco.catalog.application.command.CreateTariffCommand;
import com.telco.catalog.application.dto.ChangeTariffPriceRequest;
import com.telco.catalog.application.dto.CreateTariffRequest;
import com.telco.catalog.application.dto.TariffResponse;
import com.telco.catalog.application.query.GetTariffQuery;
import com.telco.catalog.application.query.ListTariffsQuery;
import com.telco.platform.common.api.ApiResult;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.starter.api.ApiResponseFactory;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Product catalog tariff API. Thin edge: HTTP -> command/query via {@link Mediator} -> {@link ApiResult}
 * (ADR-004, ADR-015). No business logic here.
 */
@RestController
@RequestMapping("/api/v1/tariffs")
public class TariffController {

    private final Mediator mediator;
    private final ApiResponseFactory responses;

    public TariffController(Mediator mediator, ApiResponseFactory responses) {
        this.mediator = mediator;
        this.responses = responses;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<TariffResponse> createTariff(@Valid @RequestBody CreateTariffRequest request) {
        CreateTariffCommand command = new CreateTariffCommand(
                request.code(),
                request.name(),
                request.type(),
                request.monthlyFee(),
                request.currency(),
                request.minutesIncluded(),
                request.smsIncluded(),
                request.dataMbIncluded(),
                request.targetSegment(),
                request.effectiveFrom(),
                request.effectiveTo()
        );
        return responses.ok(mediator.send(command));
    }

    /** Returns a paginated list of currently active tariffs. */
    @GetMapping
    public ApiResult<PageResult<TariffResponse>> listTariffs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return responses.ok(mediator.query(new ListTariffsQuery(page, size)));
    }

    /** Returns a single active tariff by code. Returns 404 if not found or not active. */
    @GetMapping("/{code}")
    public ApiResult<TariffResponse> getTariff(@PathVariable String code) {
        return responses.ok(mediator.query(new GetTariffQuery(code)));
    }

    @PatchMapping("/{code}/price")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<TariffResponse> changeTariffPrice(
            @PathVariable String code,
            @Valid @RequestBody ChangeTariffPriceRequest request) {
        return responses.ok(mediator.send(new ChangeTariffPriceCommand(code, request.monthlyFee())));
    }
}
