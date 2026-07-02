package com.telco.notification.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTest {

    @Test
    void create_initialises_with_pending_status_and_no_sent_at() {
        Notification n = Notification.create("user-1", "WELCOME", "SMS", "{\"key\":\"val\"}");

        assertThat(n.getId()).isNotNull();
        assertThat(n.getUserId()).isEqualTo("user-1");
        assertThat(n.getTemplateCode()).isEqualTo("WELCOME");
        assertThat(n.getChannel()).isEqualTo("SMS");
        assertThat(n.getStatus()).isEqualTo("PENDING");
        assertThat(n.getCreatedAt()).isNotNull();
        assertThat(n.getSentAt()).isNull();
        assertThat(n.getErrorMessage()).isNull();
    }

    @Test
    void mark_sent_transitions_status_to_sent_and_stamps_sent_at() {
        Notification n = Notification.create("user-1", "WELCOME", "SMS", "{}");

        n.markSent();

        assertThat(n.getStatus()).isEqualTo(Notification.STATUS_SENT);
        assertThat(n.getSentAt()).isNotNull();
        assertThat(n.getErrorMessage()).isNull();
    }

    @Test
    void mark_failed_records_error_message_and_leaves_sent_at_null() {
        Notification n = Notification.create("user-1", "WELCOME", "SMS", "{}");

        n.markFailed("provider timeout");

        assertThat(n.getStatus()).isEqualTo(Notification.STATUS_FAILED);
        assertThat(n.getErrorMessage()).isEqualTo("provider timeout");
        assertThat(n.getSentAt()).isNull();
    }

    @Test
    void mark_suppressed_sets_suppressed_status() {
        Notification n = Notification.create("user-1", "WELCOME", "SMS", "{}");

        n.markSuppressed();

        assertThat(n.getStatus()).isEqualTo(Notification.STATUS_SUPPRESSED);
        assertThat(n.getSentAt()).isNull();
        assertThat(n.getErrorMessage()).isNull();
    }
}
