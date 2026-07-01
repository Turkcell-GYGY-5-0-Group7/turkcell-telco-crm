package com.telco.subscription.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telco.platform.common.exception.BusinessRuleException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link MsisdnPool} inventory state machine: FREE -> RESERVED -> ALLOCATED and
 * release back to FREE (9.2.2, FR-13). Framework-independent.
 *
 * <p>Pool rows originate from the Flyway seed, so the aggregate exposes no public factory; the test
 * builds a FREE instance reflectively to exercise the transition guards in isolation.
 */
class MsisdnPoolStateMachineTest {

    private static MsisdnPool freePool() throws Exception {
        Constructor<MsisdnPool> ctor = MsisdnPool.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        MsisdnPool pool = ctor.newInstance();
        setField(pool, "msisdn", "905320000000");
        setField(pool, "status", MsisdnStatus.FREE);
        return pool;
    }

    private static void setField(MsisdnPool pool, String name, Object value) throws Exception {
        Field f = MsisdnPool.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(pool, value);
    }

    // --- Legal transitions ---

    @Test
    void free_can_be_reserved_then_allocated() throws Exception {
        MsisdnPool pool = freePool();
        Instant hold = Instant.now().plusSeconds(900);

        pool.reserve(hold);
        assertThat(pool.getStatus()).isEqualTo(MsisdnStatus.RESERVED);
        assertThat(pool.getReservedUntil()).isEqualTo(hold);

        pool.allocate();
        assertThat(pool.getStatus()).isEqualTo(MsisdnStatus.ALLOCATED);
        assertThat(pool.getReservedUntil()).isNull();
    }

    @Test
    void allocated_can_be_released_to_free() throws Exception {
        MsisdnPool pool = freePool();
        pool.reserve(Instant.now().plusSeconds(900));
        pool.allocate();

        pool.release();
        assertThat(pool.getStatus()).isEqualTo(MsisdnStatus.FREE);
        assertThat(pool.getReservedUntil()).isNull();
    }

    @Test
    void reserved_can_be_released_to_free() throws Exception {
        MsisdnPool pool = freePool();
        pool.reserve(Instant.now().plusSeconds(900));

        pool.release();
        assertThat(pool.getStatus()).isEqualTo(MsisdnStatus.FREE);
    }

    // --- Illegal transitions throw BusinessRuleException ---

    @Test
    void reserve_when_not_free_throws() throws Exception {
        MsisdnPool pool = freePool();
        pool.reserve(Instant.now().plusSeconds(900));
        assertThatThrownBy(() -> pool.reserve(Instant.now().plusSeconds(900)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void allocate_when_free_throws() throws Exception {
        MsisdnPool pool = freePool();
        assertThatThrownBy(pool::allocate).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void allocate_when_already_allocated_throws() throws Exception {
        MsisdnPool pool = freePool();
        pool.reserve(Instant.now().plusSeconds(900));
        pool.allocate();
        assertThatThrownBy(pool::allocate).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void release_when_already_free_throws() throws Exception {
        MsisdnPool pool = freePool();
        assertThatThrownBy(pool::release).isInstanceOf(BusinessRuleException.class);
    }
}
