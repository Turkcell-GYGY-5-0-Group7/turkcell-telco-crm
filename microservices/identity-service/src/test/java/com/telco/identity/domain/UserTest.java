package com.telco.identity.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Domain behavior for the {@link User} aggregate (feature 5.2.1). Framework-independent (ARC-02): no
 * Spring or persistence context, just the aggregate's rules.
 */
class UserTest {

    @Test
    void provisionStartsPendingWithGeneratedIdentityAndNoRoles() {
        User user = User.provision("kc-1", "ayse", "ayse@example.com");

        assertNotNull(user.getId());
        assertNotNull(user.getCreatedAt());
        assertEquals(UserStatus.PENDING, user.getStatus());
        assertEquals("kc-1", user.getKeycloakId());
        assertTrue(user.getRoles().isEmpty());
        assertTrue(user.effectivePermissions().isEmpty());
    }

    @Test
    void effectivePermissionsAreTheDeduplicatedUnionAcrossAssignedRoles() {
        Permission read = Permission.of("user:read");
        Permission write = Permission.of("user:write");
        Permission audit = Permission.of("audit:read");

        Role support = Role.of("SUPPORT");
        support.addPermission(read);

        Role admin = Role.of("ADMIN");
        admin.addPermission(read); // overlaps with SUPPORT
        admin.addPermission(write);
        admin.addPermission(audit);

        User user = User.provision("kc-2", "mehmet", "mehmet@example.com");
        user.assignRole(support);
        user.assignRole(admin);

        assertEquals(Set.of("user:read", "user:write", "audit:read"), user.effectivePermissions());
    }

    @Test
    void removingARoleRevokesItsExclusivePermissions() {
        Role admin = Role.of("ADMIN");
        admin.addPermission(Permission.of("user:write"));

        User user = User.provision("kc-3", "fatma", "fatma@example.com");
        user.assignRole(admin);
        assertTrue(user.effectivePermissions().contains("user:write"));

        user.removeRole(admin);
        assertTrue(user.effectivePermissions().isEmpty());
    }

    @Test
    void activateMovesPendingUserToActiveAndIsIdempotent() {
        User user = User.provision("kc-4", "can", "can@example.com");

        user.activate();
        assertEquals(UserStatus.ACTIVE, user.getStatus());

        user.activate();
        assertEquals(UserStatus.ACTIVE, user.getStatus());
    }

    @Test
    void lockedUserCannotBeActivatedWithoutUnlock() {
        User user = User.provision("kc-5", "deniz", "deniz@example.com");
        user.lock();
        assertEquals(UserStatus.LOCKED, user.getStatus());

        assertThrows(IllegalStateException.class, user::activate);

        user.unlock();
        assertEquals(UserStatus.ACTIVE, user.getStatus());
    }

    @Test
    void unlockOnANonLockedUserIsRejected() {
        User user = User.provision("kc-6", "ece", "ece@example.com");

        assertThrows(IllegalStateException.class, user::unlock);
    }
}
