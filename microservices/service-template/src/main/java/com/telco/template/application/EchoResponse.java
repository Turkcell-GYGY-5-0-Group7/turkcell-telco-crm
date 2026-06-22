package com.telco.template.application;

/** Response DTO for {@link EchoCommand}. */
public record EchoResponse(String echoed, int length) {
}
