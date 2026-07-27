package gh.edu.clet.sfl.fleetlogistics.dispatch.infrastructure.integration;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.port.SecurityVisibilityPort;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.DispatchExceptionCase;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.IntegrationEventPublisher;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.event.FleetEventType;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Recorded SSEMP/security-visibility adapter. Security-relevant dispatch variances (broken seal / tamper /
 * custody gap) are surfaced through the shared transactional outbox rather than by writing to any security
 * database, so the visibility commits atomically with the state change and is observable and replayable.
 * The payload carries references and classifications only — never signature binaries or seal secrets.
 */
@Component
public class RecordedSecurityVisibilityAdapter implements SecurityVisibilityPort {

    private final IntegrationEventPublisher events;

    public RecordedSecurityVisibilityAdapter(IntegrationEventPublisher events) {
        this.events = events;
    }

    @Override
    public void surfaceSecurityVariance(DispatchExceptionCase exceptionCase, ActorContext actor) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("exceptionId", exceptionCase.id());
        payload.put("exceptionNumber", exceptionCase.exceptionNumber());
        payload.put("type", exceptionCase.type().name());
        payload.put("severity", exceptionCase.severity().name());
        payload.put("status", exceptionCase.status().name());
        payload.put("dispatchId", exceptionCase.dispatchId());
        payload.put("courierItemId", exceptionCase.courierItemId());
        payload.put("visibility", "SSEMP_SECURITY");
        events.publish(FleetEventType.DISPATCH_SECURITY_VARIANCE, "DispatchExceptionCase",
                exceptionCase.id().toString(), exceptionCase.siteCode(), actor, payload);
    }
}
