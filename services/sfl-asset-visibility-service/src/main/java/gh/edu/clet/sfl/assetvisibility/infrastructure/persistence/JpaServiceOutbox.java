package gh.edu.clet.sfl.assetvisibility.infrastructure.persistence;

import java.time.Clock;
import java.util.UUID;

import gh.edu.clet.sfl.assetvisibility.application.ServiceEventType;
import gh.edu.clet.sfl.assetvisibility.application.ServiceOutbox;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
class JpaServiceOutbox implements ServiceOutbox {

    private final OutboxMessageRepository outboxMessages;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    JpaServiceOutbox(OutboxMessageRepository outboxMessages, ObjectMapper objectMapper, Clock clock) {
        this.outboxMessages = outboxMessages;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void record(String eventType, int eventVersion, String aggregateType, UUID aggregateId,
            String siteScope, String correlationId, String causationId, Object payload) {
        ServiceEventType.require(eventType, eventVersion);
        outboxMessages.save(new OutboxMessageRecord(UUID.randomUUID(), eventType, eventVersion, aggregateType,
                aggregateId, siteScope, correlationId, causationId, writeJson(payload), clock.instant()));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize the integration event", exception);
        }
    }
}