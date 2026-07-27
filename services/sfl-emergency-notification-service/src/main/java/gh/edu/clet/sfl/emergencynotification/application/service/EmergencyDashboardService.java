package gh.edu.clet.sfl.emergencynotification.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.emergencynotification.application.port.EmergencyRepository;
import gh.edu.clet.sfl.emergencynotification.application.port.RuntimeConfigurationPort;
import gh.edu.clet.sfl.emergencynotification.domain.exception.EmergencyErrorCode;
import gh.edu.clet.sfl.emergencynotification.domain.exception.EmergencyException;
import gh.edu.clet.sfl.emergencynotification.domain.model.NotificationActivation;
import gh.edu.clet.sfl.emergencynotification.domain.model.SiteCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** SRS-SFL-S174-05: emergency dashboards and CSV reports. Counts reconcile to source; stale data warns. */
@Service
public class EmergencyDashboardService {

    private final EmergencyRepository repository;
    private final EmergencyAccessPolicy access;
    private final RuntimeConfigurationPort runtimeConfig;
    private final Clock clock;

    public EmergencyDashboardService(EmergencyRepository repository, EmergencyAccessPolicy access,
            RuntimeConfigurationPort runtimeConfig, Clock clock) {
        this.repository = repository;
        this.access = access;
        this.runtimeConfig = runtimeConfig;
        this.clock = clock;
    }

    public Map<String, Object> dashboard(String site, ActorContext actor) {
        if (actor.principal().siteScopes().isEmpty()) {
            throw new EmergencyException(EmergencyErrorCode.EMERGENCY_NO_SCOPE);
        }
        access.require(actor, SflPermission.EMERGENCY_REPORT_READ, site, "EmergencyDashboard", null);
        String scoped = SiteCode.of(site).value();
        Map<String, Object> result = new LinkedHashMap<>(repository.dashboardCounts(List.of(scoped), scoped));
        result.put("stale", isStale(scoped, (Instant) result.get("sourceUpdatedAt")));
        result.put("generatedAt", clock.instant());
        return result;
    }

    /** Scheduled snapshot refresh (system actor). Multi-execution-safe: each run upserts a fresh snapshot. */
    public void refreshSnapshot(String site) {
        String scoped = SiteCode.of(site).value();
        Map<String, Object> counts = repository.dashboardCounts(List.of(scoped), scoped);
        Instant sourceUpdatedAt = (Instant) counts.get("sourceUpdatedAt");
        boolean stale = isStale(scoped, sourceUpdatedAt);
        repository.saveDashboardSnapshot("SITE:" + scoped, scoped, clock.instant(), stale, counts, sourceUpdatedAt,
                stale ? "Source data older than the configured freshness threshold" : null);
    }

    public String activationsReportCsv(String site, ActorContext actor) {
        access.require(actor, SflPermission.EMERGENCY_REPORT_EXPORT, site, "EmergencyActivationReport", null);
        StringBuilder csv = new StringBuilder(
                "activationNumber,mode,status,priority,incidentReference,approvedBy,afterActionApprovedBy,closureReason\r\n");
        for (NotificationActivation a : repository.findActivations(List.of(SiteCode.of(site).value()), null, 500)) {
            csv.append(cell(a.activationNumber())).append(',').append(a.mode()).append(',').append(a.status())
                    .append(',').append(a.priority()).append(',').append(cell(a.incidentReference())).append(',')
                    .append(cell(a.approvedBy())).append(',').append(cell(a.afterActionApprovedBy())).append(',')
                    .append(cell(a.closureReason())).append("\r\n");
        }
        return csv.toString();
    }

    private boolean isStale(String site, Instant sourceUpdatedAt) {
        if (sourceUpdatedAt == null) {
            return true;
        }
        Duration threshold = runtimeConfig.duration("emergency.dashboard.freshness-threshold", site,
                Duration.ofMinutes(5));
        return sourceUpdatedAt.isBefore(clock.instant().minus(threshold));
    }

    private static String cell(String value) {
        return "\"" + String.valueOf(value == null ? "" : value).replace("\"", "\"\"") + "\"";
    }
}
