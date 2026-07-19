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
 * Type-conditional identity validation at the registration boundary (FR-01, feature 24.5):
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
                LocalDate.of(1990, 1, 1), null, null);
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
        assertThat(violations.iterator().next().getMessage()).contains("TCKN");
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
        assertThat(violations.iterator().next().getMessage()).contains("VKN");
    }

    @Test
    void nullTypeFailsClosedOnIdentityNumberPathWithoutNpe() {
        assertThat(violationsOnIdentityNumber(request(null, VALID_TCKN))).hasSize(1);
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

    @Test
    void optionalContactFieldsAcceptValidValuesAndRejectMalformedOnes() {
        RegisterCustomerRequest withContact = new RegisterCustomerRequest(
                CustomerType.INDIVIDUAL, "Ada", "Lovelace", VALID_TCKN, LocalDate.of(1990, 1, 1),
                "ada@example.com", "+905321112233");
        assertThat(validator.validate(withContact)).isEmpty();

        RegisterCustomerRequest malformed = new RegisterCustomerRequest(
                CustomerType.INDIVIDUAL, "Ada", "Lovelace", VALID_TCKN, LocalDate.of(1990, 1, 1),
                "not-an-email", "not-a-phone");
        assertThat(validator.validate(malformed))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("email", "phone");
    }
}
