package com.telco.notification.infrastructure.config;

import com.telco.notification.domain.NotificationTemplate;
import com.telco.notification.infrastructure.persistence.NotificationTemplateRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class TemplateSeeder implements CommandLineRunner {

    private final NotificationTemplateRepository repo;

    public TemplateSeeder(NotificationTemplateRepository repo) {
        this.repo = repo;
    }

    @Override
    public void run(String... args) {
        seed("WELCOME", "SMS", "en", "Welcome",
                "Welcome to Telco CRM, {{customerName}}! Your subscription {{subscriptionId}} is now active.");
        seed("KYC_APPROVED", "SMS", "en", "KYC Approved",
                "Your KYC verification for {{customerId}} has been approved.");
        seed("KYC_REJECTED", "SMS", "en", "KYC Rejected",
                "Your KYC verification for {{customerId}} was rejected. Reason: {{reason}}.");
        seed("INVOICE_GENERATED", "EMAIL", "en", "Your Invoice is Ready",
                "Dear {{customerName}}, your invoice {{invoiceId}} for {{amount}} {{currency}} is ready.");
        seed("QUOTA_80_PERCENT", "SMS", "en", "Data Usage Alert",
                "You have used 80% of your data quota for subscription {{subscriptionId}}.");
        seed("QUOTA_EXCEEDED", "SMS", "en", "Data Quota Exceeded",
                "Your data quota for subscription {{subscriptionId}} is exhausted. Consider upgrading.");
        seed("TICKET_OPENED", "SMS", "en", "Support Ticket Created",
                "Your support ticket {{ticketId}} has been created. Team {{assignedTeam}} will assist you.");
    }

    private void seed(String code, String channel, String locale, String subject, String body) {
        if (repo.findByCodeAndChannelAndLocale(code, channel, locale).isEmpty()) {
            repo.save(NotificationTemplate.of(code, channel, locale, subject, body));
        }
    }
}
