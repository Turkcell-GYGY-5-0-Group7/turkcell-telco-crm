package com.telco.acceptance.support;

import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generates syntactically valid, unique Turkish national identity numbers (TCKN) for customer
 * registration test data (FR-01: {@code RegisterCustomerRequest.identityNumber} is validated with
 * the public TCKN checksum algorithm at the customer-service boundary).
 *
 * <p>This is an independent implementation of the publicly documented TCKN checksum spec (the same
 * algorithm customer-service's own {@code TurkishNationalId} validates against) - not a copy of
 * production code, and not a cross-module test dependency. A monotonic counter seeds each id so
 * concurrent/sequential test runs never collide on the same customer within one suite execution.
 */
public final class TurkishIdGenerator {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(new SecureRandom().nextInt(1_000_000));

    private TurkishIdGenerator() {
    }

    /** Returns a fresh, checksum-valid 11-digit TCKN with a non-zero leading digit. */
    public static String next() {
        int seed = SEQUENCE.incrementAndGet();
        int[] d = new int[9];
        d[0] = 1 + (seed % 8); // non-zero leading digit
        for (int i = 1; i < 9; i++) {
            d[i] = (seed / (i + 1)) % 10;
        }

        int oddSum = d[0] + d[2] + d[4] + d[6] + d[8];
        int evenSum = d[1] + d[3] + d[5] + d[7];
        int check10 = Math.floorMod(oddSum * 7 - evenSum, 10);

        int firstTenSum = oddSum + evenSum + check10;
        int check11 = firstTenSum % 10;

        StringBuilder sb = new StringBuilder(11);
        for (int digit : d) {
            sb.append(digit);
        }
        sb.append(check10).append(check11);
        return sb.toString();
    }
}
