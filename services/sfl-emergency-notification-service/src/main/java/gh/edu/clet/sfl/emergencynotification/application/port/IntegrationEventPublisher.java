package gh.edu.clet.sfl.emergencynotification.application.port;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.emergencynotification.domain.event.EmergencyEventType;
import java.util.Map;

/**
 * Records an event in the transactional outbox inside the caller's transaction, so the business change and
 * its event commit atomically; a drainer delivers at-least-once.
 */
public interface IntegrationEventPublisher {

    void publish(EmergencyEventType eventType, String aggregateType, String aggregateId, String siteScope,
            ActorContext actor, Map<String, Object> payload);
}
