package gh.edu.clet.sfl.emergencynotification.infrastructure.messaging;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.emergencynotification.application.port.IntegrationEventPublisher;
import gh.edu.clet.sfl.emergencynotification.domain.event.EmergencyEventType;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Writes events to the transactional outbox inside the caller's transaction (atomic with the state change). */
@Component
public class JdbcIntegrationEventPublisher implements IntegrationEventPublisher {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    public JdbcIntegrationEventPublisher(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void publish(EmergencyEventType eventType, String aggregateType, String aggregateId, String siteScope,
            ActorContext actor, Map<String, Object> payload) {
        jdbc.update("""
                INSERT INTO emergency_notification.outbox_messages (id,event_type,event_version,aggregate_type,
                    aggregate_id,site_scope,correlation_id,causation_id,payload,status,attempt_count,created_at)
                VALUES (?,?,?,?,?,?,?,?,?::jsonb,'PENDING',0,?)
                """, UUID.randomUUID(), eventType.eventType(), eventType.version(), aggregateType, aggregateId,
                siteScope, actor == null ? null : actor.correlationId(), null,
                json.writeValueAsString(payload == null ? Map.of() : payload),
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }
}
