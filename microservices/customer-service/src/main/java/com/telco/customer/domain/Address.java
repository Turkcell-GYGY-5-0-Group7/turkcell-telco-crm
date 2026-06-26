package com.telco.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * A customer's postal address (FR-03). Each address belongs to one customer; at most one address per
 * customer is the default, enforced by a partial unique index on {@code (customer_id) where is_default}.
 */
@Entity
@Table(name = "addresses")
public class Address {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(nullable = false, length = 255)
    private String line1;

    @Column(nullable = false, length = 128)
    private String city;

    @Column(length = 128)
    private String district;

    @Column(name = "postal_code", length = 16)
    private String postalCode;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    protected Address() {
        // for JPA
    }

    public Address(UUID id, UUID customerId, String line1, String city, String district,
                   String postalCode, boolean isDefault) {
        this.id = Objects.requireNonNull(id, "id");
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.line1 = Objects.requireNonNull(line1, "line1");
        this.city = Objects.requireNonNull(city, "city");
        this.district = district;
        this.postalCode = postalCode;
        this.isDefault = isDefault;
    }

    /** Adds a new address for a customer. */
    public static Address create(UUID customerId, String line1, String city, String district,
                                 String postalCode, boolean isDefault) {
        return new Address(UUID.randomUUID(), customerId, line1, city, district, postalCode, isDefault);
    }

    public void update(String line1, String city, String district, String postalCode) {
        this.line1 = Objects.requireNonNull(line1, "line1");
        this.city = Objects.requireNonNull(city, "city");
        this.district = district;
        this.postalCode = postalCode;
    }

    public void makeDefault() {
        this.isDefault = true;
    }

    public void clearDefault() {
        this.isDefault = false;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getLine1() {
        return line1;
    }

    public String getCity() {
        return city;
    }

    public String getDistrict() {
        return district;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public boolean isDefault() {
        return isDefault;
    }
}
