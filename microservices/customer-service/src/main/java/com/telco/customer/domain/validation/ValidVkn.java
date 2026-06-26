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
 * Validates that the annotated string is a checksum-valid VKN (Turkish corporate tax number). A {@code
 * null} value is considered valid; combine with {@code @NotNull}/{@code @NotBlank} as needed.
 */
@Documented
@Constraint(validatedBy = VknValidator.class)
@Target({FIELD, PARAMETER, ANNOTATION_TYPE})
@Retention(RUNTIME)
public @interface ValidVkn {

    String message() default "must be a valid VKN";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
