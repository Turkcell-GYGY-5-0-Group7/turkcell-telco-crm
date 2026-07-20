package com.telco.notification.config;

import com.telco.notification.domain.NotificationTemplate;
import com.telco.notification.infrastructure.config.TemplateSeeder;
import com.telco.notification.infrastructure.persistence.NotificationTemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateSeederTest {

    private static final String NEW_QUOTA_EXCEEDED_BODY =
            "Your data quota for subscription {{subscriptionId}} is exhausted."
                    + " Purchase a data add-on or upgrade your package to continue.";

    @Mock private NotificationTemplateRepository repo;

    @Test
    void creates_template_when_absent() {
        when(repo.findByCodeAndChannelAndLocale(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        new TemplateSeeder(repo).run();

        ArgumentCaptor<NotificationTemplate> captor = ArgumentCaptor.forClass(NotificationTemplate.class);
        verify(repo, times(8)).save(captor.capture());
        assertThat(captor.getAllValues())
                .anySatisfy(t -> {
                    assertThat(t.getCode()).isEqualTo("QUOTA_EXCEEDED");
                    assertThat(t.getBodyTemplate()).isEqualTo(NEW_QUOTA_EXCEEDED_BODY);
                });
    }

    @Test
    void updates_existing_template_when_content_differs() {
        NotificationTemplate stale = NotificationTemplate.of("QUOTA_EXCEEDED", "SMS", "en",
                "Data Quota Exceeded",
                "Your data quota for subscription {{subscriptionId}} is exhausted. Consider upgrading.");
        when(repo.findByCodeAndChannelAndLocale(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(repo.findByCodeAndChannelAndLocale("QUOTA_EXCEEDED", "SMS", "en"))
                .thenReturn(Optional.of(stale));

        new TemplateSeeder(repo).run();

        verify(repo).save(same(stale));
        assertThat(stale.getSubject()).isEqualTo("Data Quota Exceeded");
        assertThat(stale.getBodyTemplate()).isEqualTo(NEW_QUOTA_EXCEEDED_BODY);
    }

    @Test
    void does_not_save_when_existing_template_matches() {
        NotificationTemplate current = NotificationTemplate.of("QUOTA_EXCEEDED", "SMS", "en",
                "Data Quota Exceeded", NEW_QUOTA_EXCEEDED_BODY);
        when(repo.findByCodeAndChannelAndLocale(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(repo.findByCodeAndChannelAndLocale("QUOTA_EXCEEDED", "SMS", "en"))
                .thenReturn(Optional.of(current));

        new TemplateSeeder(repo).run();

        verify(repo, never()).save(same(current));
    }
}
