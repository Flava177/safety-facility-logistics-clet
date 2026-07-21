package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.messaging;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.IntegrationEventPublisher;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.event.FleetEventType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes fleet integration events to the transactional outbox.
 *
 * <p>{@code Propagation.MANDATORY} is deliberate: publishing outside a transaction would let an event
 * escape without the state change that justified it, which is exactly the failure the outbox pattern
 * exists to prevent.
 */
@Component
class OutboxIntegrationEventPublisher implements IntegrationEventPublisher {

    /** Payload schema version; bumped only when a payload shape changes incompatibly. */
    private static final int SCHEMA_VERSION = 1;
    private static final String TRACE_PARENT_KEY = "traceparent";

    private final OutboxMessageRepository outboxMessages;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    OutboxIntegrationEventPublisher(OutboxMessageRepository outboxMessages, ObjectMapper objectMapper, Clock clock) {
        this.outboxMessages = outboxMessages;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(FleetEventType eventType, String aggregateType, String aggregateId, SiteCode siteScope,
            ActorContext actor, String causationId, Object payload) {
        outboxMessages.save(new OutboxMessageEntity(
                UUID.randomUUID(),
                eventType.eventType(),
                eventType.version(),
                aggregateType == null ? eventType.defaultAggregateType() : aggregateType,
                aggregateId,
                siteScope == null ? null : siteScope.value(),
                actor == null ? null : actor.correlationId(),
                causationId,
                actor == null ? null : actor.actorId(),
                MDC.get(TRACE_PARENT_KEY),
                SCHEMA_VERSION,
                writeJson(payload),
                clock.instant()));
    }

    private String writeJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialise the fleet integration event payload", exception);
        }
    }
}
