package com.telco.reference.application;

import com.telco.platform.cqrs.Query;

import java.util.UUID;

/** Fetches a single demo item by id. */
public record GetDemoItemQuery(UUID id) implements Query<DemoItemResponse> {
}
