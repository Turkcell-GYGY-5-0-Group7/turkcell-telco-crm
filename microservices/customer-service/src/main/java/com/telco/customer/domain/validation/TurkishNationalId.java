package com.telco.customer.domain.validation;

/**
 * Checksum validation for Turkish national identity numbers (FR-01).
 *
 * <p>TCKN (individuals) is an 11-digit number whose 10th and 11th digits are checksum digits; VKN
 * (corporates) is a 10-digit number with a trailing checksum digit. This is pure domain logic with no
 * framework dependency; the Jakarta Bean Validation constraints {@link ValidTckn} and {@link ValidVkn}
 * delegate here.
 */
public final class TurkishNationalId {

    private TurkishNationalId() {
    }

    /**
     * Validates a TCKN: 11 digits, a non-zero leading digit, and the two trailing checksum digits.
     *
     * <ul>
     *   <li>digit 10 = ((d1+d3+d5+d7+d9) * 7 - (d2+d4+d6+d8)) mod 10</li>
     *   <li>digit 11 = (d1+...+d10) mod 10</li>
     * </ul>
     */
    public static boolean isValidTckn(String value) {
        if (value == null || !value.matches("\\d{11}") || value.charAt(0) == '0') {
            return false;
        }
        int[] d = toDigits(value);

        int oddSum = d[0] + d[2] + d[4] + d[6] + d[8];
        int evenSum = d[1] + d[3] + d[5] + d[7];
        int check10 = Math.floorMod(oddSum * 7 - evenSum, 10);
        if (check10 != d[9]) {
            return false;
        }

        int firstTenSum = 0;
        for (int i = 0; i < 10; i++) {
            firstTenSum += d[i];
        }
        return firstTenSum % 10 == d[10];
    }

    /**
     * Validates a VKN: 10 digits with a trailing checksum digit computed by the official Revenue
     * Administration algorithm.
     */
    public static boolean isValidVkn(String value) {
        if (value == null || !value.matches("\\d{10}")) {
            return false;
        }
        int[] d = toDigits(value);

        int sum = 0;
        for (int i = 0; i < 9; i++) {
            int tmp = (d[i] + (9 - i)) % 10;
            if (tmp != 0) {
                tmp = (tmp * powMod2(9 - i)) % 9;
                if (tmp == 0) {
                    tmp = 9;
                }
            }
            sum += tmp;
        }
        int check = (10 - (sum % 10)) % 10;
        return check == d[9];
    }

    /** 2^exp mod 9, computed iteratively to avoid floating point. */
    private static int powMod2(int exp) {
        int result = 1;
        for (int i = 0; i < exp; i++) {
            result = (result * 2) % 9;
        }
        return result;
    }

    private static int[] toDigits(String value) {
        int[] d = new int[value.length()];
        for (int i = 0; i < value.length(); i++) {
            d[i] = value.charAt(i) - '0';
        }
        return d;
    }
}
