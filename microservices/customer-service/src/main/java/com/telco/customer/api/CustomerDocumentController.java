package com.telco.customer.api;

import com.telco.customer.application.command.UploadDocumentCommand;
import com.telco.customer.application.dto.DocumentResponse;
import com.telco.customer.application.query.ListDocumentsQuery;
import com.telco.customer.domain.DocumentType;
import com.telco.platform.common.api.ApiResult;
import com.telco.platform.common.exception.ValidationException;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.starter.api.ApiResponseFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** KYC document upload (FR-03, AC-01 step 2). The binary is stored in MinIO via the mediator. */
@RestController
@RequestMapping("/api/v1/customers/{customerId}/documents")
public class CustomerDocumentController {

    private final Mediator mediator;
    private final ApiResponseFactory responses;

    public CustomerDocumentController(Mediator mediator, ApiResponseFactory responses) {
        this.mediator = mediator;
        this.responses = responses;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<DocumentResponse> upload(@PathVariable UUID customerId,
                                              @RequestParam("type") DocumentType type,
                                              @RequestParam("file") MultipartFile file) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new ValidationException("could not read uploaded file", Map.of());
        }
        return responses.ok(mediator.send(new UploadDocumentCommand(
                customerId, type, file.getOriginalFilename(), file.getContentType(), content)));
    }

    /** Lists document metadata for the customer (FR-03). Binaries are never returned inline. */
    @GetMapping
    public ApiResult<List<DocumentResponse>> list(@PathVariable UUID customerId) {
        return responses.ok(mediator.query(new ListDocumentsQuery(customerId)));
    }
}
