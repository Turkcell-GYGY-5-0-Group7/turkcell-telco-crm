package com.telco.reference.application;

import com.telco.platform.cqrs.QueryHandler;
import com.telco.reference.infrastructure.DemoItemRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/** Returns all demo items as DTOs. */
@Component
public class ListDemoItemsQueryHandler implements QueryHandler<ListDemoItemsQuery, List<DemoItemResponse>> {

    private final DemoItemRepository repository;

    public ListDemoItemsQueryHandler(DemoItemRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<DemoItemResponse> handle(ListDemoItemsQuery query) {
        return repository.findAll().stream().map(DemoItemResponse::from).toList();
    }
}
