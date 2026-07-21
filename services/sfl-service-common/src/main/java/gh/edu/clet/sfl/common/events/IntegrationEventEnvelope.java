package gh.edu.clet.sfl.common.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record IntegrationEventEnvelope(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String correlationId,
        String causationId,
        String sourceService,
        String siteScope,
        String actorId,
        String traceParent,
        Map<String, Object> payload) {
}

