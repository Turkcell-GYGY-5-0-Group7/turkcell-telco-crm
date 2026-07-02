package com.telco.notification.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTemplateTest {

    @Test
    void of_creates_template_with_all_fields_set() {
        NotificationTemplate t = NotificationTemplate.of(
                "WELCOME", "SMS", "en", "Welcome!", "Hi {{customerName}}");

        assertThat(t.getCode()).isEqualTo("WELCOME");
        assertThat(t.getChannel()).isEqualTo("SMS");
        assertThat(t.getLocale()).isEqualTo("en");
        assertThat(t.getSubject()).isEqualTo("Welcome!");
        assertThat(t.getBodyTemplate()).isEqualTo("Hi {{customerName}}");
    }

    @Test
    void of_preserves_placeholder_syntax_in_body_template() {
        NotificationTemplate t = NotificationTemplate.of(
                "INVOICE_GENERATED", "EMAIL", "tr",
                "Faturaniz Hazirlandi",
                "Fatura {{invoiceId}} tutari {{amount}} {{currency}} olarak kesilmistir.");

        assertThat(t.getBodyTemplate()).contains("{{invoiceId}}", "{{amount}}", "{{currency}}");
    }
}
