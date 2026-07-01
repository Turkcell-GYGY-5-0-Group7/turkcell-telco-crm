package com.telco.order.infrastructure.persistence;

import com.telco.order.domain.model.Order;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    static {
        POSTGRES.start();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/migration/platform")
                .load()
                .migrate();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.cloud.config.enabled", () -> "false");
        registry.add("eureka.client.enabled", () -> "false");
    }

    @Autowired private OrderRepository orderRepository;
    @Autowired private TestEntityManager entityManager;

    @Test
    void findByIdempotencyKey_returns_order_for_known_key() {
        Order order = Order.create(UUID.randomUUID(), "idem-key-001", new BigDecimal("99.99"), "sub-1");
        orderRepository.save(order);
        flushAndClear();

        assertThat(orderRepository.findByIdempotencyKey("idem-key-001")).isPresent()
                .get().extracting(Order::getId).isEqualTo(order.getId());
        assertThat(orderRepository.findByIdempotencyKey("missing")).isEmpty();
    }

    @Test
    void findByCustomerId_returns_only_orders_for_that_customer() {
        UUID customer = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        orderRepository.save(Order.create(customer, "k-1", new BigDecimal("10.00"), "sub-c"));
        orderRepository.save(Order.create(customer, "k-2", new BigDecimal("20.00"), "sub-c"));
        orderRepository.save(Order.create(other, "k-3", new BigDecimal("30.00"), "sub-o"));
        flushAndClear();

        Page<Order> result = orderRepository.findByCustomerId(customer, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2)
                .allMatch(o -> o.getCustomerId().equals(customer));
    }

    @Test
    void findByUserId_returns_only_orders_for_that_user() {
        String userId = "sub-caller";
        orderRepository.save(Order.create(UUID.randomUUID(), "k-u1", new BigDecimal("10.00"), userId));
        orderRepository.save(Order.create(UUID.randomUUID(), "k-u2", new BigDecimal("20.00"), "other-sub"));
        flushAndClear();

        Page<Order> result = orderRepository.findByUserId(userId, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1)
                .allMatch(o -> o.getUserId().equals(userId));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
