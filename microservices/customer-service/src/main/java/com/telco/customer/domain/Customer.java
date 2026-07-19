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

    // Contact info (FR-03, feature 24.5): stored plain per design-note D5 (no encryption mandate),
    // but masked in the log/persistence view (ADR-021).
    @Sensitive(MaskStrategy.EMAIL)
    @Column(name = "email", length = 255)
    private String email;

    @Sensitive(MaskStrategy.PARTIAL)
    @Column(name = "phone", length = 32)
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
                    String identityNumber, LocalDate dateOfBirth, String email, String phone,
                    CustomerStatus status, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.firstName = Objects.requireNonNull(firstName, "firstName");
        this.lastName = Objects.requireNonNull(lastName, "lastName");
        this.identityNumber = Objects.requireNonNull(identityNumber, "identityNumber");
        this.dateOfBirth = dateOfBirth;
        this.email = email;
        this.phone = phone;
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * Registers a new customer in {@link CustomerStatus#PENDING}, awaiting the KYC decision (FR-01).
     * Contact info is optional at registration (FR-03): {@code email} and {@code phone} may be null.
     */
    public static Customer register(CustomerType type, String firstName, String lastName,
                                    String identityNumber, LocalDate dateOfBirth,
                                    String email, String phone) {
        return new Customer(UUID.randomUUID(), type, firstName, lastName, identityNumber, dateOfBirth,
                email, phone, CustomerStatus.PENDING, Instant.now());
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

    /**
     * Updates mutable profile fields (FR-03), including optional contact info. The identity number is
     * immutable and not updatable. A null {@code email}/{@code phone} clears the stored value: the
     * update is a full profile replacement, consistent with {@code dateOfBirth}.
     */
    public void updateProfile(String firstName, String lastName, LocalDate dateOfBirth,
                              String email, String phone) {
        this.firstName = Objects.requireNonNull(firstName, "firstName");
        this.lastName = Objects.requireNonNull(lastName, "lastName");
        this.dateOfBirth = dateOfBirth;
        this.email = email;
        this.phone = phone;
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

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
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
