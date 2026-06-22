package com.telco.platform.autoconfigure;

/**
 * Centralized property key prefixes for the Telco CRM platform starters.
 *
 * <p>All platform configuration lives under the {@code telco.platform} root so services can
 * discover and override platform behavior consistently (ADR-018, ADR-020).
 */
public final class PlatformPropertyKeys {

    /** Root prefix for every platform configuration property. */
    public static final String ROOT = "telco.platform";

    /** API/exception-handling starter properties ({@code telco.platform.api.*}). */
    public static final String API = ROOT + ".api";

    /** Mediator starter properties ({@code telco.platform.mediator.*}). */
    public static final String MEDIATOR = ROOT + ".mediator";

    /** Security starter properties ({@code telco.platform.security.*}). */
    public static final String SECURITY = ROOT + ".security";

    /** Outbox starter properties ({@code telco.platform.outbox.*}). */
    public static final String OUTBOX = ROOT + ".outbox";

    /** Inbox starter properties ({@code telco.platform.inbox.*}). */
    public static final String INBOX = ROOT + ".inbox";

    /** Observability starter properties ({@code telco.platform.observability.*}). */
    public static final String OBSERVABILITY = ROOT + ".observability";

    /** Logging properties root ({@code telco.platform.logging.*}). */
    public static final String LOGGING = ROOT + ".logging";

    /** PII masking properties ({@code telco.platform.logging.masking.*}, ADR-021). */
    public static final String MASKING = LOGGING + ".masking";

    private PlatformPropertyKeys() {
    }
}
