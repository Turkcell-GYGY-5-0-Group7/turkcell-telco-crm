package com.telco.identity.infrastructure;

import com.telco.identity.domain.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Spring Data JPA repository for {@link Permission}, looked up by its unique code (feature 5.2.2). */
public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    Optional<Permission> findByCode(String code);
}
