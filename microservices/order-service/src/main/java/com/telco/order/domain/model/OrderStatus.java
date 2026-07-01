package com.telco.order.domain.model;

/** Lifecycle states of an {@link Order} (state machine enforced in the domain). */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    FULFILLED,
    CANCELLED,
    FAILED
}
