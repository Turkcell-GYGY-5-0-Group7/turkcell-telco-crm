package com.telco.order.infrastructure.persistence;

import com.telco.order.domain.model.SagaState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Spring Data JPA repository for {@link SagaState}. */
public interface SagaStateRepository extends JpaRepository<SagaState, UUID> {

    Optional<SagaState> findByOrderId(UUID orderId);
}
