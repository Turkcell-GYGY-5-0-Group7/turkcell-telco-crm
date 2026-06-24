package com.telco.identity.infrastructure;

import com.telco.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link User}. Lookups by keycloak_id, username, and email back the
 * identity projection and RBAC (feature 5.2.2). No credential or token queries exist - Keycloak owns
 * those (ADR-011).
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByKeycloakId(String keycloakId);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByKeycloakId(String keycloakId);
}
