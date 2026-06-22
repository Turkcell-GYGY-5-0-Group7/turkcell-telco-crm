package com.telco.template.application;

import java.time.Instant;

/** Response DTO for {@link PingQuery}. */
public record PingResponse(String status, String service, Instant timestamp) {
}
