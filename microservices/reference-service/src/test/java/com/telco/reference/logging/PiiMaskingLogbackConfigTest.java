package com.telco.reference.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.status.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the shipped {@code logback-spring.xml} loads under logback 1.5.x (correct
 * {@code class} attribute on {@code conversionRule}) and that {@code %mask}/{@code %maskEx} actually
 * mask Turkish PII in both the message and the rendered stack trace (ADR-021, Layer B).
 */
class PiiMaskingLogbackConfigTest {

    private final PrintStream originalOut = System.out;

    @AfterEach
    void restoreOut() {
        System.setOut(originalOut);
    }

    @Test
    void configLoadsAndMasksMessageAndStackTrace() throws JoranException {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        // ConsoleAppender binds System.out at start(); redirect before configuring.
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));

        LoggerContext context = new LoggerContext();
        // A bare LoggerContext has no MDC adapter; the real Spring Boot context wires one. The
        // pattern reads %X{traceId}, so provide an adapter to mirror runtime behavior.
        context.setMDCAdapter(new ch.qos.logback.classic.util.LogbackMDCAdapter());
        try (InputStream config = getClass().getResourceAsStream("/logback-spring.xml")) {
            assertThat(config).as("logback-spring.xml on classpath").isNotNull();
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            context.reset();
            configurator.doConfigure(config);

            Logger logger = context.getLogger("com.telco.reference.demo");
            logger.info("Customer with TCKN 12345678901 and email john@telco.com registered");
            logger.error("activation failed", new IllegalStateException("Customer 12345678901 not found"));
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        } finally {
            System.setOut(originalOut);
            context.stop();
        }

        // No config errors -> the conversionRule `class` attribute is valid for this logback version.
        String errors = context.getStatusManager().getCopyOfStatusList().stream()
                .filter(s -> s.getLevel() == Status.ERROR)
                .map(s -> s.getMessage() + (s.getThrowable() == null ? "" : " :: " + s.getThrowable()))
                .reduce("", (a, b) -> a + " | " + b);
        assertThat(errors).as("logback config errors").isEmpty();

        String out = captured.toString(StandardCharsets.UTF_8);
        assertThat(out).contains("*******8901");        // TCKN masked
        assertThat(out).contains("j***@***.com");        // email masked
        assertThat(out).doesNotContain("12345678901");   // raw TCKN absent everywhere, incl. stack trace
        assertThat(out).contains("Customer *******8901 not found"); // masked exception message
    }
}
