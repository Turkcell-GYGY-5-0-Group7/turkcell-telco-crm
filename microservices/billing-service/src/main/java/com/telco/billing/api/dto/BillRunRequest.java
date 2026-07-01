package com.telco.billing.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record BillRunRequest(
        @NotNull Instant periodStart,
        @NotNull Instant periodEnd
) {}
