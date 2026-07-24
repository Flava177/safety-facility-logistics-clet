package gh.edu.clet.sfl.fleetlogistics.fuel.infrastructure.integration;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.IntegrationEventPublisher;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.event.FleetEventType;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.port.FinanceAuditVisibilityPort;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelAnomalyCase;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Recorded Finance/Audit visibility adapter. It publishes a material fuel exception
 * through the transactional outbox ({@link IntegrationEventPublisher}) rather than
 * writing to any Finance system, keeping visibility atomic with the domain change and
 * observable/replayable through the existing outbox delivery pipeline.
 */
@Component
public class RecordedFinanceAuditAdapter implements FinanceAuditVisibilityPort {

    private final IntegrationEventPublisher events;

    public RecordedFinanceAuditAdapter(IntegrationEventPublisher events) {
        this.events = events;
    }

    @Override
    public void surfaceMaterialException(FuelAnomalyCase anomaly, ActorContext actor) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("anomalyId", anomaly.id());
        payload.put("anomalyNumber", anomaly.anomalyNumber());
        payload.put("type", anomaly.type().name());
        payload.put("severity", anomaly.severity().name());
        payload.put("material", anomaly.material());
        payload.put("transactionId", anomaly.transactionId());
        payload.put("vehicleId", anomaly.vehicleId());
        payload.put("driverId", anomaly.driverId());
        payload.put("visibility", "FINANCE_AUDIT");
        events.publish(FleetEventType.FUEL_EXCEPTION_DETECTED, "FuelAnomalyCase", anomaly.id().toString(),
                anomaly.siteCode(), actor, anomaly.id().toString(), payload);
    }
}
