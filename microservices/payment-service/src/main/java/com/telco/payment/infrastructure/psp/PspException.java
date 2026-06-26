package com.telco.payment.infrastructure.psp;

/**
 * Thrown by {@link PspAdapter} when the PSP returns a technical failure (network error,
 * gateway timeout, hard decline). Distinct from a soft business decline (e.g., insufficient
 * funds), which would also surface here in the MVP mock.
 *
 * <p>Caught in {@code ChargePaymentCommandHandler}: saves a FAILED attempt and transitions
 * the payment to {@code FAILED}. Not caught by the circuit breaker guard (only
 * {@link io.github.resilience4j.circuitbreaker.CallNotPermittedException} is intercepted there).
 */
public class PspException extends RuntimeException {

    public PspException(String message) {
        super(message);
    }

    public PspException(String message, Throwable cause) {
        super(message, cause);
    }
}
