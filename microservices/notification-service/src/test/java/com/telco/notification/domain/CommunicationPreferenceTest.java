package com.telco.notification.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommunicationPreferenceTest {

    @Test
    void of_creates_preference_with_correct_fields() {
        CommunicationPreference pref = CommunicationPreference.of("user-1", "SMS", true);

        assertThat(pref.getUserId()).isEqualTo("user-1");
        assertThat(pref.getChannel()).isEqualTo("SMS");
        assertThat(pref.isOptedIn()).isTrue();
        assertThat(pref.getUpdatedAt()).isNotNull();
    }

    @Test
    void of_creates_opted_out_preference() {
        CommunicationPreference pref = CommunicationPreference.of("user-2", "EMAIL", false);

        assertThat(pref.isOptedIn()).isFalse();
    }

    @Test
    void update_changes_opted_in_flag() {
        CommunicationPreference pref = CommunicationPreference.of("user-1", "SMS", true);

        pref.update(false);

        assertThat(pref.isOptedIn()).isFalse();
        assertThat(pref.getUpdatedAt()).isNotNull();
    }
}
