package gh.edu.clet.sfl.facilities.maintenance.infrastructure.integration;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.facilities.maintenance.application.FacilityFaultService;
import gh.edu.clet.sfl.facilities.maintenance.application.MaintenanceCommands;
import gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFault;
import gh.edu.clet.sfl.facilities.maintenance.domain.FaultPriority;
import gh.edu.clet.sfl.facilities.shared.application.integration.InboundIntegrationEvent;
import gh.edu.clet.sfl.facilities.shared.application.integration.IntegrationEventHandler;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * FTLMP says a vehicle is due for service; IFIMP decides that means a maintenance job.
 *
 * <p>The first cross-service reaction in the platform, and deliberately the whole of one: no queue, no
 * binding, no deduplication, no JSON. Those live once in {@code FacilitiesIntegrationListener}. What
 * is left here is only the decision — which is the part nobody can generate for you, and the part
 * that would exist in identical form if these two systems shared a process.
 *
 * <p>The next reaction is a class like this one and nothing else.
 *
 * <h2>Why this edge first</h2>
 *
 * <p>The system mapping names it: S166 Fleet & Vehicle Management integrates "Service via CMMS
 * (S153)", and SRS §21.1 lists Vehicle → Service Record among the relationships crossing a module
 * boundary. It is also the only named edge where both ends are built — S162a fire-safety would be the
 * more dramatic demonstration and cannot be done, because S162a does not exist to publish anything.
 *
 * <h2>An event, not a command</h2>
 *
 * <p>Fleet does not tell facilities to raise a fault. It states something true about its own record,
 * and facilities decides what that means here. Change the maintenance policy — a different priority,
 * an auto-generated work order, a dashboard entry instead of a fault — and only this class changes.
 * Fleet is neither consulted nor redeployed.
 *
 * <h2>The vehicle is the location</h2>
 *
 * <p>A fault needs a room or a location code, and a vehicle is neither a room nor a facility asset.
 * The registration goes in as {@code locationCode}, which is what a workshop job card carries and
 * what a technician will look for. No foreign key crosses the schema boundary: the vehicle id travels
 * by value in the description and facilities never resolves it against fleet's tables.
 *
 * <h2>Two kinds of repeat, two different guards</h2>
 *
 * <p><strong>Redelivery</strong> — the same message twice — is stopped by the inbox, before this runs.
 *
 * <p><strong>Recurrence</strong> — the daily compliance sweep republishing while a vehicle stays due —
 * is not, because each sweep produces a genuinely new message with its own id. Left alone that raises
 * a fault a day, forever. The idempotency key below is derived from the vehicle and its service state
 * rather than from the message, so every sweep replays the original fault. A vehicle that goes DUE, is
 * serviced, and later goes DUE again correctly gets a second fault: the key carries the state, and the
 * fault it would have replayed has been closed by then.
 */
@Component
public class VehicleServiceDueHandler implements IntegrationEventHandler {

    private static final Logger log = LoggerFactory.getLogger(VehicleServiceDueHandler.class);

    private static final String DUE = "sfl.ftlmp.vehicle-service-due.v1";
    private static final String OVERDUE = "sfl.ftlmp.vehicle-service-overdue.v1";

    /**
     * The actor the fault is reported by.
     *
     * <p>A service account rather than a person, and {@code serviceAccount = true} so an audit reader
     * can tell a fault the platform raised from one somebody walked past and reported. Site scope is
     * {@code *} because the publisher decides the site, not this consumer — it has to accept an event
     * for any site FTLMP operates.
     */
    private static final SiteScopedPrincipal SYSTEM = new SiteScopedPrincipal(
            "system.fleet-integration", "Fleet integration", Set.of(SflRole.SFL_ADMIN), Set.of("*"), true);

    private final FacilityFaultService faults;

    public VehicleServiceDueHandler(FacilityFaultService faults) {
        this.faults = faults;
    }

    @Override
    public boolean handles(String eventType) {
        return DUE.equals(eventType) || OVERDUE.equals(eventType);
    }

    @Override
    public void handle(InboundIntegrationEvent event) {
        String vehicleId = event.text("vehicleId") != null ? event.text("vehicleId") : event.aggregateId();
        String serviceStatus = event.text("serviceStatus");
        // The envelope header is the more reliable of the two — the publisher always sets it, whereas
        // the payload field depends on which code path raised the event.
        String siteCode = event.siteCode() != null ? event.siteCode() : event.text("siteCode");

        if (vehicleId == null || siteCode == null) {
            // Nothing can be raised from this and retrying will not make the fields appear, so it is
            // logged and dropped rather than thrown — throwing would loop it forever.
            log.error("{} lacks a vehicle id and a site code; no fault can be raised", event.eventType());
            return;
        }

        // Degrade rather than discard.
        //
        // A missing registration used to drop the event entirely, which meant a real maintenance job
        // vanished because a payload field was absent — and exactly that happened, because two
        // publishers of this event emitted different bodies. The publisher is fixed; this is the
        // second line of defence, because losing a maintenance job silently is a far worse failure
        // than raising one whose title reads a little worse.
        String registration = event.text("registrationNumber");
        if (registration == null) {
            log.warn("{} carried no registrationNumber; falling back to the vehicle id for {}",
                    event.eventType(), vehicleId);
            registration = "Vehicle " + vehicleId;
        }

        boolean overdue = OVERDUE.equals(event.eventType());
        FacilityFault fault = faults.report(new MaintenanceCommands.ReportFault(
                siteCode,
                null,
                registration,
                null,
                (overdue ? "Vehicle service overdue — " : "Vehicle service due — ") + registration,
                describe(registration, vehicleId, serviceStatus, event.text("odometer")),
                "FLEET_SERVICE",
                overdue ? FaultPriority.HIGH : FaultPriority.MEDIUM,
                new ActorContext(SYSTEM, event.correlationId() == null
                        ? UUID.randomUUID().toString()
                        : event.correlationId()),
                // INTEGRATION rather than SYSTEM: this did not originate inside facilities, it arrived
                // from another service. An auditor asking where a fault came from gets the answer from
                // the channel rather than by inference.
                SourceChannel.INTEGRATION,
                "fleet-service:" + vehicleId + ":" + serviceStatus,
                Map.of("vehicleId", vehicleId, "serviceStatus", String.valueOf(serviceStatus))));

        log.info("Raised {} from {} for vehicle {} ({})", fault.faultNumber(), event.eventType(),
                registration, serviceStatus);
    }

    private static String describe(String registration, String vehicleId, String serviceStatus,
            String odometer) {
        return "Raised automatically from SFL.FTLMP. Vehicle " + registration
                + " reported service status " + serviceStatus + "."
                + (odometer == null ? "" : " Odometer at the time: " + odometer + " km.")
                + " Fleet vehicle reference " + vehicleId
                + ". Triage and convert to a work order if the job is to be done by the maintenance team.";
    }
}
