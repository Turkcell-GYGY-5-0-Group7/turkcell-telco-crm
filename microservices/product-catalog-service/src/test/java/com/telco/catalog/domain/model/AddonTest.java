package com.telco.catalog.domain.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.assertj.core.api.Assertions.assertThat;

class AddonTest {

    @Test
    void getStatus_defaults_to_active_when_backing_field_is_null() throws Exception {
        // Addon has no public factory; use the protected JPA constructor via reflection.
        Constructor<Addon> ctor = Addon.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        Addon addon = ctor.newInstance();

        assertThat(addon.getStatus()).isEqualTo("ACTIVE");
    }
}
