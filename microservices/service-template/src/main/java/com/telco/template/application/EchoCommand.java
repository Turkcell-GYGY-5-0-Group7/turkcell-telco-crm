package com.telco.template.application;

import com.telco.platform.cqrs.Command;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Sample state-changing command. The bean-validation constraints are enforced automatically by the
 * mediator ValidationBehavior before the handler runs (ADR-008).
 */
public record EchoCommand(
        @NotBlank @Size(max = 280) String message
) implements Command<EchoResponse> {
}
