package com.telco.reference.application;

import com.telco.platform.common.exception.CommonErrorCode;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.QueryHandler;
import com.telco.reference.infrastructure.DemoItemRepository;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Reads a demo item. A missing id raises {@link ResourceNotFoundException}, which starter-api maps
 * to an HTTP 404 {@code ApiResult} error (ADR-015).
 */
@Component
public class GetDemoItemQueryHandler implements QueryHandler<GetDemoItemQuery, DemoItemResponse> {

    private final DemoItemRepository repository;

    public GetDemoItemQueryHandler(DemoItemRepository repository) {
        this.repository = repository;
    }

    @Override
    public DemoItemResponse handle(GetDemoItemQuery query) {
        return repository.findById(query.id())
                .map(DemoItemResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND, "Demo item not found",
                        Map.of("id", query.id().toString())));
    }
}
