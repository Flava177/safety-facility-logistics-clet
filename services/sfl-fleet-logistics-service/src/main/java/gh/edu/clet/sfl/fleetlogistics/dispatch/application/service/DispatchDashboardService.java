package gh.edu.clet.sfl.fleetlogistics.dispatch.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.port.DispatchRepository;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.CourierItem;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.DispatchExceptionCase;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.RuntimeConfigurationPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * S171 operational dashboards and CSV reports. Totals are computed from the source operational tables and
 * carry a stale-data flag derived from the configurable dashboard-freshness threshold, so the console can
 * warn when a snapshot is stale. Dashboard totals reconcile to source records.
 */
@Service
public class DispatchDashboardService {

    private final DispatchRepository repository;
    private final DispatchAccessPolicy access;
    private final RuntimeConfigurationPort runtimeConfig;
    private final Clock clock;

    public DispatchDashboardService(DispatchRepository repository, DispatchAccessPolicy access,
            RuntimeConfigurationPort runtimeConfig, Clock clock) {
        this.repository = repository;
        this.access = access;
        this.runtimeConfig = runtimeConfig;
        this.clock = clock;
    }

    public Map<String, Object> dashboard(String site, ActorContext actor) {
        access.require(actor, SflPermission.DISPATCH_REPORT_READ, site, "DispatchDashboard", null);
        String scoped = SiteCode.of(site).value();
        Map<String, Object> result = new LinkedHashMap<>(repository.dashboardCounts(List.of(scoped), scoped));
        result.put("stale", isStale(scoped, (Instant) result.get("sourceUpdatedAt")));
        result.put("generatedAt", clock.instant());
        return result;
    }

    /** Scheduled snapshot refresh (system actor). Multi-execution-safe: each run upserts a fresh snapshot row. */
    public void refreshSnapshot(String site) {
        String scoped = SiteCode.of(site).value();
        Map<String, Object> counts = repository.dashboardCounts(List.of(scoped), scoped);
        Instant sourceUpdatedAt = (Instant) counts.get("sourceUpdatedAt");
        boolean stale = isStale(scoped, sourceUpdatedAt);
        repository.saveDashboardSnapshot("SITE:" + scoped, scoped, clock.instant(), stale, counts, sourceUpdatedAt,
                stale ? "Source data older than the configured freshness threshold" : null);
    }

    public String itemsReportCsv(String site, ActorContext actor) {
        access.require(actor, SflPermission.DISPATCH_REPORT_EXPORT, site, "DispatchItemReport", null);
        StringBuilder csv = new StringBuilder(
                "itemNumber,direction,itemType,sensitivity,chainOfCustody,status,origin,destination,handler,undelivered\r\n");
        for (CourierItem i : drain(paging -> repository.findItems(new DispatchRepository.ItemQuery(
                List.of(SiteCode.of(site).value()), null, null, null, null, null, null, null, null, null, null, paging)))) {
            csv.append(cell(i.itemNumber())).append(',').append(i.direction()).append(',').append(i.itemType())
                    .append(',').append(i.sensitivity()).append(',').append(i.chainOfCustodyRequired()).append(',')
                    .append(i.status()).append(',').append(cell(i.origin())).append(',').append(cell(i.destination()))
                    .append(',').append(cell(i.assignedHandler())).append(',').append(i.undelivered()).append("\r\n");
        }
        return csv.toString();
    }

    public String exceptionsReportCsv(String site, ActorContext actor) {
        access.require(actor, SflPermission.DISPATCH_REPORT_EXPORT, site, "DispatchExceptionReport", null);
        StringBuilder csv = new StringBuilder(
                "exceptionNumber,type,severity,status,securityRelevant,assignee,slaDueAt,dispatchId,courierItemId,escalationLevel\r\n");
        for (DispatchExceptionCase e : drain(paging -> repository.findExceptions(new DispatchRepository.ExceptionQuery(
                List.of(SiteCode.of(site).value()), null, null, null, null, null, null, null, null, null, null,
                paging)))) {
            csv.append(cell(e.exceptionNumber())).append(',').append(e.type()).append(',').append(e.severity())
                    .append(',').append(e.status()).append(',').append(e.securityRelevant()).append(',')
                    .append(cell(e.assignee())).append(',').append(e.slaDueAt()).append(',')
                    .append(e.dispatchId() == null ? "" : e.dispatchId()).append(',')
                    .append(e.courierItemId() == null ? "" : e.courierItemId()).append(',')
                    .append(e.escalationLevel()).append("\r\n");
        }
        return csv.toString();
    }

    /**
     * Every record matching the query, a page at a time.
     *
     * <p>Both exports used to stop at the first 500 rows with nothing in the file, the headers or the
     * response to say so — a site with more activity than that got a quietly incomplete compliance
     * export. Draining the pages is the fix; {@code EXPORT_CAP} is a runaway guard rather than a
     * limit anybody is expected to reach, and reaching it is reported rather than swallowed.
     */
    private static final int EXPORT_CAP = 50_000;

    private <T> List<T> drain(java.util.function.Function<DispatchRepository.Paging,
            DispatchRepository.DispatchPage<T>> fetch) {
        List<T> all = new java.util.ArrayList<>();
        int page = 0;
        while (all.size() < EXPORT_CAP) {
            var result = fetch.apply(new DispatchRepository.Paging(page, DispatchRepository.Paging.MAX_SIZE, null));
            all.addAll(result.content());
            if (result.content().isEmpty() || page >= result.totalPages() - 1) {
                break;
            }
            page++;
        }
        return all;
    }

    private boolean isStale(String site, Instant sourceUpdatedAt) {
        if (sourceUpdatedAt == null) return true;
        return sourceUpdatedAt.isBefore(clock.instant().minus(runtimeConfig.dashboardFreshnessThreshold(site)));
    }

    private static String cell(String value) {
        return "\"" + String.valueOf(value == null ? "" : value).replace("\"", "\"\"") + "\"";
    }
}
