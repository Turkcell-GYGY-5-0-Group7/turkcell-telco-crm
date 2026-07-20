package com.telco.order.domain.model;

/**
 * What an order provisions (FR-09). NEW_LINE runs the paid saga (payment -> MSISDN -> activation);
 * PLAN_CHANGE and ADDON target an existing subscription, carry no upfront payment, and bill on the
 * next monthly invoice (FR-22).
 */
public enum OrderType {
    NEW_LINE,
    PLAN_CHANGE,
    ADDON
}
