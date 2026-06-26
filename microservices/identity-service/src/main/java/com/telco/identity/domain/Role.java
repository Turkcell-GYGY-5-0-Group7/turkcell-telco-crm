package com.telco.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A named collection of {@link Permission}s assigned to {@link User}s (for example {@code ADMIN}).
 * Roles mirror the Keycloak realm roles but carry the application-specific permission grants used to
 * resolve a user's effective permissions (FR-IAM-04).
 *
 * <p>Framework-independent domain type (ARC-02); JPA annotations map the {@code roles} table and the
 * {@code role_permissions} join. Identity is the {@code name}. Permissions are fetched eagerly: RBAC
 * cardinality is small and effective-permission resolution must work outside an open session.
 */
@Entity
@Table(name = "roles")
public class Role {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 128)
    private String name;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> permissions = new LinkedHashSet<>();

    protected Role() {
        // for JPA
    }

    public Role(UUID id, String name) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
    }

    /** Creates a role with a fresh identifier for the given name. */
    public static Role of(String name) {
        return new Role(UUID.randomUUID(), name);
    }

    /** Grants a permission to this role. Idempotent: re-adding the same code is a no-op. */
    public void addPermission(Permission permission) {
        permissions.add(Objects.requireNonNull(permission, "permission"));
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /** Unmodifiable view of the permissions granted to this role. */
    public Set<Permission> getPermissions() {
        return Collections.unmodifiableSet(permissions);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Role role)) {
            return false;
        }
        return Objects.equals(name, role.name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }
}
