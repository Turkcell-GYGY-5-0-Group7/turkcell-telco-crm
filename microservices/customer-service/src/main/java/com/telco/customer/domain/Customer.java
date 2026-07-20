package com.telco.customer.domain;

import com.telco.customer.infrastructure.crypto.IdentityNumberCryptoConverter;
import com.telco.platform.common.exception.BusinessRuleException;
import com.telco.platform.common.masking.MaskStrategy;
import com.telco.platform.common.masking.Sensitive;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Customer master record and aggregate root for the KYC lifecycle (FR-01..FR-04).
 *
 * <p>The KYC state machine is a domain invariant (FR-02): a customer is created {@link
 * CustomerStatus#PENDING} and the only legal transitions are PENDING -> {@link CustomerStatus#ACTIVE}
 * (approve) and PENDING -> {@link CustomerStatus#REJECTED} (reject). Any other transition raises {@link
 * BusinessRuleException}. Deletion is a soft-delete (FR-04): {@link #markDeleted()} stamps {@code
 * deletedAt} and the row is retained.
 *
 * <p>The national identity number (TCKN/VKN) is PII: it is encrypted at rest with AES-GCM via {@link
 * IdentityNumberCryptoConverter} (NFR-06) and masked in the log/persistence view via {@link Sensitive}
 * (ADR-021). The domain holds the plaintext; the database column {@code identity_number_enc} holds only
 * ciphertext.
 *
 * <p>Framework-independent domain type (ARC-02): the KYC behavior carries no Spring dependency; JPA
 * annotations only describe the mapping.
 */
@Entity
@Table(name = "customers")
// Soft-delete (FR-04): every default query excludes deleted rows; the row itself is retained.
@SQLRestriction("deleted_at IS NULL")
public class Customer {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CustomerType type;

    @Column(name = "first_name", nullable = false, length = 128)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 128)
    private String lastName;

    @Convert(converter = IdentityNumberCryptoConverter.class)
    @Sensitive(MaskStrategy.PARTIAL)
    @Column(name = "identity_number_enc", nullable = false)
    private String identityNumber;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    /** Contact e-mail (FR-03). PII: masked in logs via DTO-level {@code @Sensitive}, never logged raw. */
    @Column(length = 255)
    private String email;

    /** Contact phone in E.164-ish free form (FR-03). Same PII handling as {@link #email}. */
    @Column(length = 32)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CustomerStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Customer() {
        // for JPA
    }

    public Customer(UUID id, CustomerType type, String firstName, String lastName,
                    String identityNumber, LocalDate dateOfBirth, CustomerStatus status,
                    Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.firstName = Objects.requireNonNull(firstName, "firstName");
        this.lastName = Objects.requireNonNull(lastName, "lastName");
        this.identityNumber = Objects.requireNonNull(identityNumber, "identityNumber");
        this.dateOfBirth = dateOfBirth;
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /** Registers a new customer in {@link CustomerStatus#PENDING}, awaiting the KYC decision (FR-01). */
    public static Customer register(CustomerType type, String firstName, String lastName,
                                    String identityNumber, LocalDate dateOfBirth) {
        return new Customer(UUID.randomUUID(), type, firstName, lastName, identityNumber, dateOfBirth,
                CustomerStatus.PENDING, Instant.now());
    }

    /**
     * Approves KYC, transitioning PENDING -> ACTIVE (FR-02). Rejects the call with {@link
     * BusinessRuleException} when the customer is not PENDING.
     */
    public void approveKyc() {
        requirePending("approve KYC");
        status = CustomerStatus.ACTIVE;
    }

    /**
     * Rejects KYC, transitioning PENDING -> REJECTED (FR-02). Rejects the call with {@link
     * BusinessRuleException} when the customer is not PENDING.
     */
    public void rejectKyc() {
        requirePending("reject KYC");
        status = CustomerStatus.REJECTED;
    }

    private void requirePending(String operation) {
        if (status != CustomerStatus.PENDING) {
            throw new BusinessRuleException(
                    "Cannot " + operation + ": customer is " + status + ", expected PENDING");
        }
    }

    /** Updates mutable profile fields (FR-03). The identity number is immutable and not updatable. */
    public void updateProfile(String firstName, String lastName, LocalDate dateOfBirth) {
        this.firstName = Objects.requireNonNull(firstName, "firstName");
        this.lastName = Objects.requireNonNull(lastName, "lastName");
        this.dateOfBirth = dateOfBirth;
    }

    /** Updates contact information (FR-03). Both fields are optional; null clears the value. */
    public void updateContact(String email, String phone) {
        this.email = email;
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    /** Soft-deletes the customer (FR-04). Idempotent: re-deleting keeps the original timestamp. */
    public void markDeleted() {
        if (deletedAt == null) {
            deletedAt = Instant.now();
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public CustomerType getType() {
        return type;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getIdentityNumber() {
        return identityNumber;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public CustomerStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
