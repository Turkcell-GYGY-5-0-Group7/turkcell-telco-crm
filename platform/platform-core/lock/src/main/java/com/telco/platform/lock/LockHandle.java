package com.telco.platform.lock;

/**
 * A held {@link DistributedLock} lock, releasable via try-with-resources.
 */
public interface LockHandle extends AutoCloseable {

    /** The key this handle holds the lock for. */
    String key();

    /** Releases the lock. Idempotent: releasing an already-released handle is a no-op. */
    void release();

    @Override
    default void close() {
        release();
    }
}
