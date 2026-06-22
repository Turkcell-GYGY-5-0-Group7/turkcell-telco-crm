package com.telco.reference.application;

import com.telco.reference.domain.DemoItem;

import java.time.Instant;
import java.util.UUID;

/** Read DTO for a demo item. Domain entities are never exposed directly (ADR-015). */
public record DemoItemResponse(UUID id, String name, Instant createdAt) {

    public static DemoItemResponse from(DemoItem item) {
        return new DemoItemResponse(item.getId(), item.getName(), item.getCreatedAt());
    }
}
