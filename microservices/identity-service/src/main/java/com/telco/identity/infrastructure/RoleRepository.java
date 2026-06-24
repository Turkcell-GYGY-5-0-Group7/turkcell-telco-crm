package com.telco.identity.infrastructure;

import com.telco.identity.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Spring Data JPA repository for {@link Role}, looked up by its unique name (feature 5.2.2). */
public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByName(String name);
}
