package com.telco.reference.application;

import com.telco.platform.cqrs.Query;

import java.util.List;

/** Lists all demo items. A real service would paginate (ADR-015 offset/cursor). */
public record ListDemoItemsQuery() implements Query<List<DemoItemResponse>> {
}
