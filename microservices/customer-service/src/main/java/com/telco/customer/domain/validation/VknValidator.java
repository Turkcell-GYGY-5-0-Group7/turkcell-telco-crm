package com.telco.customer.domain.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Delegates {@link ValidVkn} to {@link TurkishNationalId#isValidVkn(String)}. */
public class VknValidator implements ConstraintValidator<ValidVkn, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Null is valid here; presence is enforced separately by @NotNull/@NotBlank.
        return value == null || TurkishNationalId.isValidVkn(value);
    }
}
