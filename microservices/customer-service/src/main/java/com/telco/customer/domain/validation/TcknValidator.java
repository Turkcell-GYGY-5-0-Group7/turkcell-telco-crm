package com.telco.customer.domain.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Delegates {@link ValidTckn} to {@link TurkishNationalId#isValidTckn(String)}. */
public class TcknValidator implements ConstraintValidator<ValidTckn, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Null is valid here; presence is enforced separately by @NotNull/@NotBlank.
        return value == null || TurkishNationalId.isValidTckn(value);
    }
}
