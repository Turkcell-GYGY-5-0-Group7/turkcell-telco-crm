package com.telco.order.infrastructure.persistence;

import com.telco.order.domain.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Spring Data JPA repository for {@link Order}. */
public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);

    Page<Order> findByUserId(String userId, Pageable pageable);
}
