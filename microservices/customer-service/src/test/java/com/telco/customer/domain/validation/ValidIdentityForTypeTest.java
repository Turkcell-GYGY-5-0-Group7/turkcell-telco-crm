package com.telco.customer.domain.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.telco.customer.application.dto.RegisterCustomerRequest;
import com.telco.customer.domain.CustomerType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.Set;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Type-conditional identity validation at the registration boundary (FR-01):
 * INDIVIDUAL requires a checksum-valid TCKN, CORPORATE a checksum-valid VKN, and the violation
 * surfaces on the {@code identityNumber} property path (same error shape clients saw with the former
 * field-level {@code @ValidTckn}). Checksums themselves are covered by {@link TurkishNationalIdTest}.
 */
class ValidIdentityForTypeTest {

    private static final String VALID_TCKN = "10000000146";
    private static final String VALID_VKN = "1234567890";

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        // ParameterMessageInterpolator avoids the jakarta.el dependency of the default interpolator;
        // none of the message templates here use EL expressions.
        validator = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory()
                .getValidator();
    }

    private static RegisterCustomerRequest request(CustomerType type, String identityNumber) {
        return new RegisterCustomerRequest(type, "Ada", "Lovelace", identityNumber,
                LocalDate.of(1990, 1, 1));
    }

    private static Set<ConstraintViolation<RegisterCustomerRequest>> violationsOnIdentityNumber(
            RegisterCustomerRequest request) {
        return validator.validate(request).stream()
                .filter(v -> "identityNumber".equals(v.getPropertyPath().toString()))
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void individualWithValidTcknPasses() {
        assertThat(validator.validate(request(CustomerType.INDIVIDUAL, VALID_TCKN))).isEmpty();
    }

    @Test
    void individualWithVknFailsOnIdentityNumberPath() {
        Set<ConstraintViolation<RegisterCustomerRequest>> violations =
                violationsOnIdentityNumber(request(CustomerType.INDIVIDUAL, VALID_VKN));

        assertThat(violations).hasSize(1);
    }

    @Test
    void corporateWithValidVknPasses() {
        assertThat(validator.validate(request(CustomerType.CORPORATE, VALID_VKN))).isEmpty();
    }

    @Test
    void corporateWithTcknFailsOnIdentityNumberPath() {
        Set<ConstraintViolation<RegisterCustomerRequest>> violations =
                violationsOnIdentityNumber(request(CustomerType.CORPORATE, VALID_TCKN));

        assertThat(violations).hasSize(1);
    }

    @Test
    void nullTypeIsLeftToNotNullWithoutNpe() {
        // Null type skips the class-level check (it can't pick TCKN vs VKN); @NotNull on the type
        // field reports the violation instead, on "type" rather than "identityNumber".
        assertThat(violationsOnIdentityNumber(request(null, VALID_TCKN))).isEmpty();
        assertThat(validator.validate(request(null, VALID_TCKN)))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("type");
    }

    @Test
    void blankIdentityNumberIsLeftToNotBlank() {
        Set<ConstraintViolation<RegisterCustomerRequest>> violations =
                validator.validate(request(CustomerType.INDIVIDUAL, " "));

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsOnly("identityNumber");
        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .noneMatch(m -> m.contains("TCKN"));
    }
}
