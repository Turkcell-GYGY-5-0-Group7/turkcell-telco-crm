package com.telco.template.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Handlers are plain objects, so they unit-test without Spring (ADR-013). This keeps the build green
 * without requiring any infrastructure.
 */
class EchoCommandHandlerTest {

    private final EchoCommandHandler handler = new EchoCommandHandler();

    @Test
    void echoesMessageAndReportsLength() {
        EchoResponse response = handler.handle(new EchoCommand("hello"));
        assertEquals("hello", response.echoed());
        assertEquals(5, response.length());
    }
}
