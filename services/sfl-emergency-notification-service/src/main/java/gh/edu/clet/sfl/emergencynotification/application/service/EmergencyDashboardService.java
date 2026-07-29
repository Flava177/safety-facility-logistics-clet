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

    /** The dashboard's counts split by status, priority, mode and channel. Closes gap 12. */
    public Map<String, Map<String, Long>> breakdown(String site, ActorContext actor) {
        access.require(actor, SflPermission.EMERGENCY_REPORT_READ, site, "EmergencyDashboard", null);
        return repository.dashboardBreakdown(List.of(SiteCode.of(site).value()), site);
    }

    public String activationsReportCsv(String site, ActorContext actor) {
        access.require(actor, SflPermission.EMERGENCY_REPORT_EXPORT, site, "EmergencyActivationReport", null);
        StringBuilder csv = new StringBuilder(
                "activationNumber,mode,status,priority,incidentReference,approvedBy,afterActionApprovedBy,closureReason\r\n");
        for (NotificationActivation a : drain(site)) {
            csv.append(cell(a.activationNumber())).append(',').append(a.mode()).append(',').append(a.status())
                    .append(',').append(a.priority()).append(',').append(cell(a.incidentReference())).append(',')
                    .append(cell(a.approvedBy())).append(',').append(cell(a.afterActionApprovedBy())).append(',')
                    .append(cell(a.closureReason())).append("\r\n");
        }
        return csv.toString();
    }

    /**
     * Every activation at the site, a page at a time.
     *
     * <p>Closes gap 11 — the only gap on this service with a compliance consequence rather than an
     * operational one. The export stopped at the first 500 activations with nothing in the file, the
     * headers or the response to say so, so a busy site got a quietly incomplete compliance export
     * and neither the screen nor the file could tell. {@code EXPORT_CAP} is a runaway guard rather
     * than a limit anybody is expected to reach.
     */
    private static final int EXPORT_CAP = 50_000;

    private List<NotificationActivation> drain(String site) {
        List<NotificationActivation> all = new java.util.ArrayList<>();
        int page = 0;
        while (all.size() < EXPORT_CAP) {
            var result = repository.findActivations(new EmergencyRepository.ActivationQuery(
                    List.of(SiteCode.of(site).value()), null, null, null, null, null, null, null, null, null, null,
                    null, new EmergencyRepository.Paging(page, EmergencyRepository.Paging.MAX_SIZE, "createdAt")));
            all.addAll(result.content());
            if (result.content().isEmpty() || page >= result.totalPages() - 1) {
                break;
            }
            page++;
        }
        return all;
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
