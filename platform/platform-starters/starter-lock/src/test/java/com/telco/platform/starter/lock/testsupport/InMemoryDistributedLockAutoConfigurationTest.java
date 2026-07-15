package com.telco.platform.starter.lock.testsupport;

import com.telco.platform.lock.DistributedLock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves {@link InMemoryDistributedLockAutoConfiguration}'s activation condition: it must supply a
 * working {@link DistributedLock} bean when a consuming service's test profile explicitly disables
 * the real Redisson autoconfiguration (feature 17.3/17.4 code-review follow-up, 2026-07-12), and must
 * stay out of the way otherwise - both when the property is unset (production shape, real
 * autoconfiguration applies) and when a test explicitly re-enables it against a real Redis
 * (a {@code *ConcurrencyIT}).
 */
class InMemoryDistributedLockAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(InMemoryDistributedLockAutoConfiguration.class));

    @Test
    void activatesOnlyWhenLockIsExplicitlyDisabled() {
        contextRunner
                .withPropertyValues("telco.platform.lock.enabled=false")
                .run(context -> assertThat(context).hasSingleBean(DistributedLock.class));
    }

    @Test
    void doesNotActivateWhenThePropertyIsUnset() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(DistributedLock.class));
    }

    @Test
    void doesNotActivateWhenLockIsExplicitlyEnabled() {
        contextRunner
                .withPropertyValues("telco.platform.lock.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(DistributedLock.class));
    }
}
