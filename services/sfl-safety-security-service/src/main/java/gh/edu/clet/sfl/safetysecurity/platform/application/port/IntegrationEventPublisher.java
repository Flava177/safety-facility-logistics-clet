package gh.edu.clet.sfl.safetysecurity.platform.application.port;

import gh.edu.clet.sfl.common.security.ActorContext;
import java.util.Map;

/**
 * Records an event in the {@code safety_security.outbox_messages} transactional outbox inside the
 * caller's transaction, so the business change and its event commit atomically; a drainer delivers
 * at-least-once. Shared across every {@code safety_security} module, the same way {@link AuditPort}
 * is - unlike S174's per-module {@code emergency_notification.outbox_messages}.
 *
 * <p>Event type is a plain string rather than a module-specific enum (contrast S174's {@code
 * IntegrationEventPublisher}, typed to {@code EmergencyEventType}), because this port is shared by
 * modules that each own their own event catalog enum.
 */
public interface IntegrationEventPublisher {

    void publish(String eventType, int eventVersion, String aggregateType, String aggregateId, String siteScope,
            ActorContext actor, Map<String, Object> payload);
}
