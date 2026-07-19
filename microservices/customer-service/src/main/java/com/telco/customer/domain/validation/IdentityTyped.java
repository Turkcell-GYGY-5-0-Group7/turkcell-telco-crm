package com.telco.customer.domain.validation;

import com.telco.customer.domain.CustomerType;

/**
 * Contract for {@link ValidIdentityForType} targets: exposes the declared customer type and the raw
 * identity number so {@link IdentityForTypeValidator} can pick the matching checksum (TCKN or VKN)
 * without depending on any concrete request DTO. Record accessors satisfy it for free.
 */
public interface IdentityTyped {

    CustomerType type();

    String identityNumber();
}
