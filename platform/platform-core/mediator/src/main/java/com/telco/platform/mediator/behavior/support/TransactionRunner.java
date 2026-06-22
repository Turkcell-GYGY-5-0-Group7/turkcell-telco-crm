package com.telco.platform.mediator.behavior.support;

import java.util.function.Supplier;

/**
 * Port for executing an action within a transaction. The Spring adapter lives in starter-mediator.
 */
public interface TransactionRunner {

    /** Runs the supplied action inside a transaction and returns its result. */
    <R> R executeInTransaction(Supplier<R> action);
}
