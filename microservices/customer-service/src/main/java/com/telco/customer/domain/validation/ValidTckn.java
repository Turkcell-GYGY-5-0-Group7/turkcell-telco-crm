package com.telco.customer.domain.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Validates that the annotated string is a checksum-valid TCKN (Turkish individual identity number),
 * rejecting checksum-invalid and wrong-length values with a 400 at the API boundary (FR-01). A {@code
 * null} value is considered valid; combine with {@code @NotNull}/{@code @NotBlank} as needed.
 */
@Documented
@Constraint(validatedBy = TcknValidator.class)
@Target({FIELD, PARAMETER, ANNOTATION_TYPE})
@Retention(RUNTIME)
public @interface ValidTckn {

    String message() default "must be a valid TCKN";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
