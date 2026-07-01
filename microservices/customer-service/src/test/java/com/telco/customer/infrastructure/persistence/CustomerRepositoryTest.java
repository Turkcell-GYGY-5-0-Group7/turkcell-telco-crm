package com.telco.customer.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.telco.customer.domain.Address;
import com.telco.customer.domain.Customer;
import com.telco.customer.domain.CustomerType;
import com.telco.customer.infrastructure.crypto.AesKeyProvider;
import com.telco.customer.infrastructure.crypto.IdentityNumberCryptoConverter;
import java.time.LocalDate;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Slice test for the customer repositories against a real PostgreSQL container with the production
 * Flyway schema applied (feature 6.2.4, ADR-013). Verifies soft-delete: a deleted customer is excluded
 * from default reads (enforced by {@code @SQLRestriction}) while its row is retained with {@code
 * deleted_at} set, and that the one-default-address constraint holds.
 *
 * <p>Flyway (not Hibernate) owns the schema, mirroring production {@code spring.flyway.locations}.
 * The crypto converter and key provider are imported explicitly because the {@code @DataJpaTest} slice
 * does not scan {@code @Component} beans, and the {@link Customer} mapping requires the converter.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({IdentityNumberCryptoConverter.class, AesKeyProvider.class})
class CustomerRepositoryTest {

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
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.cloud.config.enabled", () -> "false");
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("customer.crypto.aes-key",
                () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    }

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private AddressRepository addresses;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void softDeleteExcludesFromDefaultReadsButRetainsTheRow() {
        Customer customer = customers.save(Customer.register(
                CustomerType.INDIVIDUAL, "Ada", "Lovelace", "10000000146", LocalDate.of(1990, 1, 1)));
        UUID id = customer.getId();
        flushAndClear();

        assertThat(customers.findById(id)).isPresent();

        Customer loaded = customers.findById(id).orElseThrow();
        loaded.markDeleted();
        customers.save(loaded);
        flushAndClear();

        assertThat(customers.findById(id)).isEmpty();

        Object rowCount = entityManager.getEntityManager()
                .createNativeQuery(
                        "SELECT count(*) FROM customers WHERE id = :id AND deleted_at IS NOT NULL")
                .setParameter("id", id)
                .getSingleResult();
        assertThat(((Number) rowCount).longValue()).isEqualTo(1L);
    }

    @Test
    void identityNumberIsStoredAsCiphertextNotPlaintext() {
        Customer customer = customers.save(Customer.register(
                CustomerType.INDIVIDUAL, "Grace", "Hopper", "11111111110", LocalDate.of(1985, 5, 5)));
        flushAndClear();

        Object stored = entityManager.getEntityManager()
                .createNativeQuery("SELECT identity_number_enc FROM customers WHERE id = :id")
                .setParameter("id", customer.getId())
                .getSingleResult();

        assertThat(stored).isInstanceOf(String.class);
        assertThat((String) stored).isNotEqualTo("11111111110");
        assertThat(customers.findById(customer.getId()).orElseThrow().getIdentityNumber())
                .isEqualTo("11111111110");
    }

    @Test
    void findsDefaultAddressForCustomer() {
        Customer customer = customers.save(Customer.register(
                CustomerType.INDIVIDUAL, "Edsger", "Dijkstra", "10000000146", LocalDate.of(1970, 3, 3)));
        flushAndClear();

        addresses.save(Address.create(customer.getId(), "Sok 1", "Istanbul", "Kadikoy", "34000", true));
        addresses.save(Address.create(customer.getId(), "Sok 2", "Ankara", "Cankaya", "06000", false));
        flushAndClear();

        assertThat(addresses.findByCustomerId(customer.getId())).hasSize(2);
        assertThat(addresses.findByCustomerIdAndIsDefaultTrue(customer.getId()))
                .get()
                .extracting(Address::getCity)
                .isEqualTo("Istanbul");
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
