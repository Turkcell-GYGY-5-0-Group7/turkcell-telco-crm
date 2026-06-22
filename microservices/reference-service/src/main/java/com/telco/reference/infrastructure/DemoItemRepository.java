package com.telco.reference.infrastructure;

import com.telco.reference.domain.DemoItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Spring Data JPA repository for {@link DemoItem}. */
public interface DemoItemRepository extends JpaRepository<DemoItem, UUID> {
}
