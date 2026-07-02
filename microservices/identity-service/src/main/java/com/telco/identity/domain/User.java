package com.telco.identity.domain;

import com.telco.platform.common.masking.MaskStrategy;
import com.telco.platform.common.masking.Sensitive;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Identity projection of a user and the aggregate root for role assignment (FR-IAM-04).
 *
 * <p>Credentials and the login/refresh flow belong to Keycloak (ADR-011): this aggregate stores no
 * password and no tokens. {@code keycloakId} links the projection to the Keycloak user; provisioning
 * and realm-role changes are performed through the Keycloak Admin API. The {@code email} is PII and is
 * masked in the log/persistence view via {@link Sensitive} (ADR-021); it keeps its real value in API
 * responses and events.
 *
 * <p>Framework-independent domain type (ARC-02): behavior (role assignment, effective-permission
 * resolution, status transitions) lives here with no Spring dependency; JPA annotations only describe
 * the mapping. Roles are fetched eagerly so {@link #effectivePermissions()} resolves outside an open
 * session.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @Column(name = "keycloak_id", nullable = false, unique = true, length = 255)
    private String keycloakId;

    @Column(nullable = false, length = 255)
    private String username;

    @Sensitive(MaskStrategy.EMAIL)
    @Column(nullable = false, length = 320)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new LinkedHashSet<>();

    protected User() {
        // for JPA
    }

    public User(UUID id, String keycloakId, String username, String email, UserStatus status,
                Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.keycloakId = Objects.requireNonNull(keycloakId, "keycloakId");
        this.username = Objects.requireNonNull(username, "username");
        this.email = Objects.requireNonNull(email, "email");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * Provisions a new user in {@link UserStatus#PENDING} for a Keycloak identity. The
     * {@code keycloakId} is obtained from the Keycloak Admin API before calling this factory.
     */
    public static User provision(String keycloakId, String username, String email) {
        return new User(UUID.randomUUID(), keycloakId, username, email, UserStatus.PENDING,
                Instant.now());
    }

    /** Assigns a role. Idempotent: re-assigning the same role is a no-op. */
    public void assignRole(Role role) {
        roles.add(Objects.requireNonNull(role, "role"));
    }

    /** Removes a role. Removing a role the user does not hold is a no-op. */
    public void removeRole(Role role) {
        roles.remove(Objects.requireNonNull(role, "role"));
    }

    /**
     * Resolves the effective permission codes granted to this user, flattened and de-duplicated across
     * all assigned roles (FR-IAM-04).
     */
    public Set<String> effectivePermissions() {
        return roles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getCode)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Enables the user. Allowed from {@link UserStatus#PENDING} or {@link UserStatus#ACTIVE}; a
     * {@link UserStatus#LOCKED} user must be {@link #unlock() unlocked} first.
     */
    public void activate() {
        if (status == UserStatus.LOCKED) {
            throw new IllegalStateException("Cannot activate a locked user; unlock first");
        }
        status = UserStatus.ACTIVE;
    }

    /** Locks the user, denying access. Idempotent when already locked. */
    public void lock() {
        status = UserStatus.LOCKED;
    }

    /** Unlocks a locked user, returning it to {@link UserStatus#ACTIVE}. */
    public void unlock() {
        if (status != UserStatus.LOCKED) {
            throw new IllegalStateException("Cannot unlock a user that is not locked");
        }
        status = UserStatus.ACTIVE;
    }

    /** Soft-deletes the user. Cannot delete an already-deleted user. */
    public void delete() {
        if (status == UserStatus.DELETED) {
            throw new IllegalStateException("User is already deleted");
        }
        status = UserStatus.DELETED;
    }

    public UUID getId() {
        return id;
    }

    public String getKeycloakId() {
        return keycloakId;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Unmodifiable view of the roles assigned to this user. */
    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
    }
}
