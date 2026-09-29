package gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.scheduling;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.facilities.buildingsystems.application.BmsDeviceService;
import gh.edu.clet.sfl.facilities.buildingsystems.application.BuildingAlertService;
import gh.edu.clet.sfl.facilities.buildingsystems.application.TelemetryIngestionService;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The four S156 sweeps, on a timer. Thin by design, like {@code BookingScheduledJobs}: build a system actor,
 * call the service, log what moved. Every decision lives in the services, testable without a thread.
 *
 * <ul>
 *   <li><strong>Sustained breaches</strong> - every 30 s ({@code sfl.buildingsystems.debounce.interval-ms}).
 *       Raises breaches whose debounce elapsed with no further reading. Its interval bounds how late past
 *       the debounce window a slow-reporting sensor's alert can be.</li>
 *   <li><strong>Offline devices</strong> - every 60 s ({@code sfl.buildingsystems.offline.interval-ms}).</li>
 *   <li><strong>Lifecycle reminders</strong> - hourly ({@code sfl.buildingsystems.lifecycle.interval-ms}).</li>
 *   <li><strong>Retention purge</strong> - daily ({@code sfl.buildingsystems.retention.interval-ms}).</li>
 * </ul>
 *
 * <p>All run on platform threads (RLS scope {@code *}) and are idempotent, so the intervals are latency
 * choices, and two instances sweeping together waste a query rather than double-raise anything. Critical
 * faults are not swept - they escalate on the reading that shows them. {@code sfl.buildingsystems.scheduling.enabled=false}
 * removes the bean entirely.
 */
@Component
@ConditionalOnProperty(name = "sfl.buildingsystems.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class BuildingSystemsScheduledJobs {

    private static final Logger log = LoggerFactory.getLogger(BuildingSystemsScheduledJobs.class);

    private static final SiteScopedPrincipal SYSTEM = new SiteScopedPrincipal(
            "system.buildingsystems", "Building systems scheduler", Set.of(SflRole.SFL_ADMIN), Set.of("*"), true);

    private final BuildingAlertService alerts;
    private final BmsDeviceService devices;
    private final TelemetryIngestionService telemetry;

    public BuildingSystemsScheduledJobs(BuildingAlertService alerts, BmsDeviceService devices,
            TelemetryIngestionService telemetry) {
        this.alerts = alerts;
        this.devices = devices;
        this.telemetry = telemetry;
    }

    @Scheduled(fixedDelayString = "${sfl.buildingsystems.debounce.interval-ms:30000}",
            initialDelayString = "${sfl.buildingsystems.debounce.initial-delay-ms:120000}")
    public void promoteSustainedBreaches() {
        run("sustained-breach", alerts::promoteSustainedBreaches);
    }

    @Scheduled(fixedDelayString = "${sfl.buildingsystems.offline.interval-ms:60000}",
            initialDelayString = "${sfl.buildingsystems.offline.initial-delay-ms:150000}")
    public void sweepOfflineDevices() {
        run("offline", alerts::sweepOfflineDevices);
    }

    @Scheduled(fixedDelayString = "${sfl.buildingsystems.lifecycle.interval-ms:3600000}",
            initialDelayString = "${sfl.buildingsystems.lifecycle.initial-delay-ms:180000}")
    public void sweepLifecycleReminders() {
        run("lifecycle-reminder", devices::sweepLifecycleReminders);
    }

    @Scheduled(fixedDelayString = "${sfl.buildingsystems.retention.interval-ms:86400000}",
            initialDelayString = "${sfl.buildingsystems.retention.initial-delay-ms:600000}")
    public void purgeExpiredReadings() {
        run("retention", telemetry::purgeExpiredReadings);
    }

    private void run(String sweep, Function<ActorContext, Integer> work) {
        try {
            int moved = work.apply(new ActorContext(SYSTEM, UUID.randomUUID().toString()));
            if (moved > 0) {
                log.info("S156 {} sweep acted on {} record(s)", sweep, moved);
            }
        } catch (RuntimeException failure) {
            // Swallowed on purpose: an uncaught exception cancels a fixedDelay schedule for the life of the
            // process, and the offline sweep is the one whose silent death nobody would notice.
            log.error("S156 {} sweep failed; it will be retried on the next run", sweep, failure);
        }
    }
}
