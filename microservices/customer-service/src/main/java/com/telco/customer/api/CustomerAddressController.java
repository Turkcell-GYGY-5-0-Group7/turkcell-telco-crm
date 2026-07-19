package com.telco.customer.api;

import com.telco.customer.application.command.AddAddressCommand;
import com.telco.customer.application.command.DeleteAddressCommand;
import com.telco.customer.application.command.SetDefaultAddressCommand;
import com.telco.customer.application.command.UpdateAddressCommand;
import com.telco.customer.application.dto.AddressRequest;
import com.telco.customer.application.dto.AddressResponse;
import com.telco.customer.application.query.ListAddressesQuery;
import com.telco.platform.common.api.ApiResult;
import com.telco.platform.cqrs.Unit;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.starter.api.ApiResponseFactory;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Customer address management (FR-03). Thin edge over the mediator. */
@RestController
@RequestMapping("/api/v1/customers/{customerId}/addresses")
public class CustomerAddressController {

    private final Mediator mediator;
    private final ApiResponseFactory responses;

    public CustomerAddressController(Mediator mediator, ApiResponseFactory responses) {
        this.mediator = mediator;
        this.responses = responses;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<AddressResponse> add(@PathVariable UUID customerId,
                                          @Valid @RequestBody AddressRequest request) {
        return responses.ok(mediator.send(new AddAddressCommand(customerId, request.line1(),
                request.city(), request.district(), request.postalCode(), request.isDefault())));
    }

    @GetMapping
    public ApiResult<List<AddressResponse>> list(@PathVariable UUID customerId) {
        return responses.ok(mediator.query(new ListAddressesQuery(customerId)));
    }

    @PutMapping("/{addressId}")
    public ApiResult<AddressResponse> update(@PathVariable UUID customerId,
                                             @PathVariable UUID addressId,
                                             @Valid @RequestBody AddressRequest request) {
        return responses.ok(mediator.send(new UpdateAddressCommand(customerId, addressId,
                request.line1(), request.city(), request.district(), request.postalCode())));
    }

    @DeleteMapping("/{addressId}")
    public ApiResult<Unit> delete(@PathVariable UUID customerId, @PathVariable UUID addressId) {
        return responses.ok(mediator.send(new DeleteAddressCommand(customerId, addressId)));
    }

    @PostMapping("/{addressId}/default")
    public ApiResult<Unit> setDefault(@PathVariable UUID customerId, @PathVariable UUID addressId) {
        return responses.ok(mediator.send(new SetDefaultAddressCommand(customerId, addressId)));
    }
}
