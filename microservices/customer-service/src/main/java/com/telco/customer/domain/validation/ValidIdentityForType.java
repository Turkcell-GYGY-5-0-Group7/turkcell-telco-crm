package com.telco.customer.domain.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Class-level constraint validating the identity number against the declared customer type (FR-01):
 * INDIVIDUAL requires a checksum-valid TCKN, CORPORATE a checksum-valid VKN. A null or unrecognized
 * type fails closed. The violation is reported on the {@code identityNumber} property so API clients
 * see the same field-level error shape as the former field-level {@code @ValidTckn}.
 *
 * <p>A null/blank identity number is considered valid here; presence is enforced separately by
 * {@code @NotBlank} on the field.
 */
@Documented
@Constraint(validatedBy = IdentityForTypeValidator.class)
@Target({TYPE, ANNOTATION_TYPE})
@Retention(RUNTIME)
public @interface ValidIdentityForType {

    String message() default "identity number does not match the customer type";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
