package com.telco.customer.domain.validation;

import com.telco.customer.domain.CustomerType;

/**
 * Contract for inputs carrying a customer type plus an identity number, so
 * {@link ValidIdentityForType} can validate the number against the right algorithm
 * (TCKN for INDIVIDUAL, VKN for CORPORATE - FR-01). Records satisfy it through their
 * generated accessors.
 */
public interface IdentityBearing {

    CustomerType type();

    String identityNumber();
}
