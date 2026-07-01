package com.telco.notification.infrastructure.persistence;

import com.telco.notification.domain.NotificationTemplate;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface NotificationTemplateRepository extends MongoRepository<NotificationTemplate, String> {
    Optional<NotificationTemplate> findByCodeAndChannelAndLocale(String code, String channel, String locale);
    Optional<NotificationTemplate> findByCodeAndChannel(String code, String channel);
}
