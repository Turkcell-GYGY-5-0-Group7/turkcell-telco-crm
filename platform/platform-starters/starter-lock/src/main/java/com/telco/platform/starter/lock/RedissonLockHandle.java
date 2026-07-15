package com.telco.platform.starter.lock;

import com.telco.platform.lock.LockHandle;
import org.redisson.api.RLock;

/** {@link LockHandle} wrapping a held Redisson {@link RLock}. */
final class RedissonLockHandle implements LockHandle {

    private final String key;
    private final RLock rLock;

    RedissonLockHandle(String key, RLock rLock) {
        this.key = key;
        this.rLock = rLock;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public void release() {
        // isHeldByCurrentThread guards against double-release (idempotent for the ordinary case
        // per the LockHandle contract) and against unlocking a lease that already hard-expired
        // (avoids IllegalMonitorStateException rather than catching it). Both isHeldByCurrentThread
        // and unlock() are network calls: a genuine mid-hold Redis outage can still surface here as
        // an unchecked Redisson connection exception, not swallowed by this guard.
        if (rLock.isHeldByCurrentThread()) {
            rLock.unlock();
        }
    }
}
