package com.telco.customer.domain.validation;

import com.telco.customer.domain.CustomerType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Delegates {@link ValidIdentityForType} to the matching {@link TurkishNationalId} checksum:
 * {@link CustomerType#INDIVIDUAL} -> TCKN, {@link CustomerType#CORPORATE} -> VKN. A null type fails
 * closed (never an NPE); the violation always surfaces on the {@code identityNumber} property path.
 */
public class IdentityForTypeValidator implements ConstraintValidator<ValidIdentityForType, IdentityTyped> {

    @Override
    public boolean isValid(IdentityTyped value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        String identityNumber = value.identityNumber();
        if (identityNumber == null || identityNumber.isBlank()) {
            // Presence is enforced separately by @NotBlank on the field.
            return true;
        }

        CustomerType type = value.type();
        boolean valid;
        String message;
        if (type == CustomerType.INDIVIDUAL) {
            valid = TurkishNationalId.isValidTckn(identityNumber);
            message = "must be a checksum-valid TCKN for an INDIVIDUAL customer";
        } else if (type == CustomerType.CORPORATE) {
            valid = TurkishNationalId.isValidVkn(identityNumber);
            message = "must be a checksum-valid VKN for a CORPORATE customer";
        } else {
            // Null/unknown type: fail closed; @NotNull reports the missing type on its own field.
            valid = false;
            message = "cannot be validated without a customer type";
        }

        if (!valid) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(message)
                    .addPropertyNode("identityNumber")
                    .addConstraintViolation();
        }
        return valid;
    }
}
