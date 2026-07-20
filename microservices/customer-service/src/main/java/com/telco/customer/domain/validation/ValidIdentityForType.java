package com.telco.customer.domain.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level constraint: the identity number must be a valid TCKN for INDIVIDUAL customers and a
 * valid VKN for CORPORATE customers (FR-01). Replaces the former field-level {@code @ValidTckn},
 * which rejected every corporate/VKN registration regardless of validity.
 */
@Documented
@Constraint(validatedBy = IdentityForTypeValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidIdentityForType {

    String message() default "identityNumber is not valid for the given customer type";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
