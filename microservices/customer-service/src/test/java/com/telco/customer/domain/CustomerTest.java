package com.telco.customer.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.telco.platform.common.exception.BusinessRuleException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CustomerTest {

    private static Customer pendingCustomer() {
        return Customer.register(CustomerType.INDIVIDUAL, "Ada", "Lovelace", "10000000146",
                LocalDate.of(1990, 1, 1), null, null);
    }

    @Test
    void registersInPendingStatus() {
        Customer customer = pendingCustomer();
        assertEquals(CustomerStatus.PENDING, customer.getStatus());
        assertNotNull(customer.getId());
        assertFalse(customer.isDeleted());
    }

    @Test
    void approveTransitionsPendingToActive() {
        Customer customer = pendingCustomer();
        customer.approveKyc();
        assertEquals(CustomerStatus.ACTIVE, customer.getStatus());
    }

    @Test
    void rejectTransitionsPendingToRejected() {
        Customer customer = pendingCustomer();
        customer.rejectKyc();
        assertEquals(CustomerStatus.REJECTED, customer.getStatus());
    }

    @Test
    void approveFromActiveIsRejectedAsBusinessRuleViolation() {
        Customer customer = pendingCustomer();
        customer.approveKyc();
        assertThrows(BusinessRuleException.class, customer::approveKyc);
    }

    @Test
    void rejectFromRejectedIsRejectedAsBusinessRuleViolation() {
        Customer customer = pendingCustomer();
        customer.rejectKyc();
        assertThrows(BusinessRuleException.class, customer::rejectKyc);
    }

    @Test
    void updateProfileReplacesContactInfo() {
        Customer customer = Customer.register(CustomerType.INDIVIDUAL, "Ada", "Lovelace",
                "10000000146", LocalDate.of(1990, 1, 1), "ada@example.com", "+905321112233");
        assertEquals("ada@example.com", customer.getEmail());
        assertEquals("+905321112233", customer.getPhone());

        customer.updateProfile("Ada", "Lovelace", LocalDate.of(1990, 1, 1),
                "countess@example.com", null);

        assertEquals("countess@example.com", customer.getEmail());
        assertNull(customer.getPhone());
    }

    @Test
    void markDeletedStampsTimestampAndIsIdempotent() {
        Customer customer = pendingCustomer();
        customer.markDeleted();
        assertTrue(customer.isDeleted());

        var firstStamp = customer.getDeletedAt();
        customer.markDeleted();
        assertEquals(firstStamp, customer.getDeletedAt());
    }
}
