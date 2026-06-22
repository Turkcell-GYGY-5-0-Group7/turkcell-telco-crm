package com.telco.reference.application;

import com.telco.platform.cqrs.Command;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Creates a demo item. Validated by the mediator before the handler runs. */
public record CreateDemoItemCommand(
        @NotBlank @Size(max = 280) String name
) implements Command<DemoItemResponse> {
}
