package com.telco.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * A granular authorization permission (for example {@code user:read}). Permissions are granted to
 * {@link Role}s and resolved transitively for a {@link User} (FR-IAM-04).
 *
 * <p>Framework-independent domain type (ARC-02); the JPA annotations only describe the mapping to the
 * {@code permissions} table and carry no Spring dependency. Identity is the {@code code}: two
 * permissions are equal when their codes match.
 */
@Entity
@Table(name = "permissions")
public class Permission {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 128)
    private String code;

    protected Permission() {
        // for JPA
    }

    public Permission(UUID id, String code) {
        this.id = Objects.requireNonNull(id, "id");
        this.code = Objects.requireNonNull(code, "code");
    }

    /** Creates a permission with a fresh identifier for the given code. */
    public static Permission of(String code) {
        return new Permission(UUID.randomUUID(), code);
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Permission permission)) {
            return false;
        }
        return Objects.equals(code, permission.code);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(code);
    }
}
