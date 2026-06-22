package com.telco.platform.cqrs;

/**
 * The single-valued result type for commands that produce no meaningful return value.
 */
public final class Unit {

    /** The sole instance. */
    public static final Unit INSTANCE = new Unit();

    private Unit() {
    }
}
