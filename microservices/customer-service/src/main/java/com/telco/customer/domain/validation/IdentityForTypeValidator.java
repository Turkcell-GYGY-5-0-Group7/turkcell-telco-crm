package com.telco.customer.domain.validation;

import com.telco.customer.domain.CustomerType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Delegates {@link ValidIdentityForType} to {@link TurkishNationalId}: TCKN checksum for
 * INDIVIDUAL, VKN checksum for CORPORATE. Null type or number is valid here; presence is
 * enforced separately by {@code @NotNull}/{@code @NotBlank} (same convention as
 * {@link TcknValidator}/{@link VknValidator}). The violation is reported on the
 * {@code identityNumber} property so API error shapes stay field-addressed.
 */
public class IdentityForTypeValidator
        implements ConstraintValidator<ValidIdentityForType, IdentityBearing> {

    @Override
    public boolean isValid(IdentityBearing value, ConstraintValidatorContext context) {
        if (value == null || value.type() == null || value.identityNumber() == null) {
            return true;
        }
        boolean valid = value.type() == CustomerType.CORPORATE
                ? TurkishNationalId.isValidVkn(value.identityNumber())
                : TurkishNationalId.isValidTckn(value.identityNumber());
        if (!valid) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                            context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("identityNumber")
                    .addConstraintViolation();
        }
        return valid;
    }
}
