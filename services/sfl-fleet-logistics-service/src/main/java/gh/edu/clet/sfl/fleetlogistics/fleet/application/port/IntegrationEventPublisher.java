package gh.edu.clet.sfl.fleetlogistics.fleet.application.port;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.event.FleetEventType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;

/**
 * Outbound port for publishing fleet integration events.
 *
 * <p>The implementation writes to {@code fleet_logistics.outbox_messages} inside the caller's
 * transaction so the business change and its event commit atomically; a scheduled drainer then
 * delivers to the broker at-least-once.
 */
public interface IntegrationEventPublisher {

    /**
     * Records an event for publication.
     *
     * @param causationId identifier of the command or inbound message that caused this event; may be null
     */
    void publish(FleetEventType eventType, String aggregateType, String aggregateId, SiteCode siteScope,
            ActorContext actor, String causationId, Object payload);

    /** Convenience overload for events caused directly by the current command. */
    default void publish(FleetEventType eventType, String aggregateType, String aggregateId, SiteCode siteScope,
            ActorContext actor, Object payload) {
        publish(eventType, aggregateType, aggregateId, siteScope, actor, null, payload);
    }
}
