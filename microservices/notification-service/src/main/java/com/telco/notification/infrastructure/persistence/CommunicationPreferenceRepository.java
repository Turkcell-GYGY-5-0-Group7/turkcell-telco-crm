package com.telco.notification.infrastructure.persistence;

import com.telco.notification.domain.CommunicationPreference;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface CommunicationPreferenceRepository extends MongoRepository<CommunicationPreference, String> {
    Optional<CommunicationPreference> findByUserIdAndChannel(String userId, String channel);
    List<CommunicationPreference> findByUserId(String userId);
}
