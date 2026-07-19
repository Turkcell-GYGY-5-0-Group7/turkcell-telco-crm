package com.telco.customer.application.dto;

import com.telco.customer.domain.CustomerType;
import com.telco.customer.domain.validation.IdentityTyped;
import com.telco.customer.domain.validation.ValidIdentityForType;
import com.telco.platform.common.masking.MaskStrategy;
import com.telco.platform.common.masking.Sensitive;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Registration input (FR-01). The identity number is validated at the boundary against the declared
 * customer type ({@link ValidIdentityForType}: INDIVIDUAL -> TCKN, CORPORATE -> VKN) and masked in
 * the log view via {@link Sensitive} (ADR-021). Contact info is optional (FR-03, feature 24.5) and
 * likewise masked in logs.
 */
@ValidIdentityForType
public record RegisterCustomerRequest(
        @NotNull CustomerType type,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank @Sensitive(MaskStrategy.PARTIAL) String identityNumber,
        @Past LocalDate dateOfBirth,
        @Email @Size(max = 255) @Sensitive(MaskStrategy.EMAIL) String email,
        @Pattern(regexp = "^\\+?[0-9]{7,15}$") @Size(max = 32)
        @Sensitive(MaskStrategy.PARTIAL) String phone
) implements IdentityTyped {
}
