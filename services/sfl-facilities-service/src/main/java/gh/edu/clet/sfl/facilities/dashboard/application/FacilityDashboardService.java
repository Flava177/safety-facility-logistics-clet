package gh.edu.clet.sfl.facilities.dashboard.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.dashboard.application.ports.MaintenanceReadModel;
import gh.edu.clet.sfl.facilities.dashboard.domain.FacilityDashboard;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.AssetCriticality;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityAsset;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.masterdata.domain.LocationReadinessStatus;
import gh.edu.clet.sfl.facilities.masterdata.domain.Site;
import gh.edu.clet.sfl.facilities.readiness.application.ports.ReadinessRepository;
import gh.edu.clet.sfl.facilities.readiness.domain.BlockerSeverity;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessBlocker;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.port.RuntimeConfigurationPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.OperatingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The S152-05 dashboard.
 *
 * <p>Computed from the source tables on request rather than served from a snapshot. That is the
 * decision the requirement's own wording pushes towards — "dashboard counts must reconcile to source
 * workflow/read-model records" — and at Phase 1 volumes a live read is both cheaper to reason about
 * and impossible to serve stale by accident. Snapshots exist (V8) for go-live reporting, which is a
 * different job: proving what the numbers were on a date after the estate has moved on.
 *
 * <p>Every threshold is read from runtime configuration at evaluation time (NFR 23.8), so a site can
 * tighten its staleness window during an examination without a redeploy.
 */
@Service
public class FacilityDashboardService {

    private static final Duration DEFAULT_READINESS_STALENESS = Duration.ofDays(7);
    private static final Duration DEFAULT_EXAM_STALENESS = Duration.ofHours(24);
    private static final Duration DEFAULT_CRITICAL_ESCALATION = Duration.ofHours(4);
    private static final Duration DEFAULT_SERVICE_WARNING = Duration.ofDays(14);
    private static final Duration DEFAULT_WARRANTY_WARNING = Duration.ofDays(30);

    private final FacilitiesRepository facilities;
    private final ReadinessRepository readiness;
    private final MaintenanceReadModel maintenance;
    private final RuntimeConfigurationPort configuration;
    private final FacilitiesAuthorization authorization;
    private final Clock clock;

    public FacilityDashboardService(FacilitiesRepository facilities, ReadinessRepository readiness,
            MaintenanceReadModel maintenance, RuntimeConfigurationPort configuration,
            FacilitiesAuthorization authorization, Clock clock) {
        this.facilities = facilities;
        this.readiness = readiness;
        this.maintenance = maintenance;
        this.configuration = configuration;
        this.authorization = authorization;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FacilityDashboard dashboard(String siteCode, ActorContext actor, SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_DASHBOARD_READ, channel, "FacilityDashboard",
                "read", siteCode);
        authorization.requireRequestedSite(actor, siteCode, channel, "FacilityDashboard");

        Instant now = clock.instant();
        OperatingMode mode = operatingModeOf(siteCode);

        List<FacilityRoom> rooms = authorization.filterBySite(actor, facilities.findActiveRooms(siteCode),
                FacilityRoom::siteCode);
        List<FacilityAsset> assets = authorization.filterBySite(actor, facilities.findActiveAssets(siteCode),
                FacilityAsset::siteCode);
        List<ReadinessBlocker> openBlockers = authorization.filterBySite(actor,
                readiness.findOpenBlockersForSite(siteCode), ReadinessBlocker::siteCode);

        Duration stalenessWindow = mode == OperatingMode.EXAMINATION
                ? configuration.duration("facilities.readiness.examination-staleness-threshold", siteCode,
                        DEFAULT_EXAM_STALENESS)
                : configuration.duration("facilities.readiness.staleness-threshold", siteCode,
                        DEFAULT_READINESS_STALENESS);
        Instant stalenessThreshold = now.minus(stalenessWindow);
        Duration escalationWindow = configuration.duration("facilities.blocker.critical-escalation-window",
                siteCode, DEFAULT_CRITICAL_ESCALATION);

        List<FacilityRoom> staleRooms = rooms.stream()
                .filter(room -> room.readinessUpdatedAt() == null
                        || room.readinessUpdatedAt().isBefore(stalenessThreshold))
                .toList();

        Set<String> maintenanceLocations = maintenance.locationCodesWithOpenWork(siteCode);
        MaintenanceReadModel.OpenWork openWork = maintenance.openWorkFor(siteCode);

        return new FacilityDashboard(
                siteCode == null || siteCode.isBlank() ? "*" : siteCode.strip().toUpperCase(Locale.ROOT),
                mode,
                now,
                spaceReadiness(rooms),
                blockerSummary(openBlockers, now, escalationWindow),
                assetSummary(assets, now, siteCode),
                new FacilityDashboard.MaintenanceSummary(openWork.openFaults(), openWork.openWorkOrders()),
                readinessScore(rooms),
                !staleRooms.isEmpty(),
                staleRooms.isEmpty() ? null : staleWarning(staleRooms.size(), stalenessWindow),
                examinationRisks(rooms, openBlockers, maintenanceLocations),
                unavailableSpaces(rooms),
                staleRows(staleRooms, now));
    }

    /** The drilldown behind the blockers indicator, worst and oldest first. */
    @Transactional(readOnly = true)
    public List<FacilityDashboard.ExceptionRow> blockerRows(String siteCode, ActorContext actor,
            SourceChannel channel) {
        requireDrilldown(actor, channel, siteCode);
        return authorization.filterBySite(actor, readiness.findOpenBlockersForSite(siteCode),
                        ReadinessBlocker::siteCode).stream()
                .sorted(Comparator.comparing(ReadinessBlocker::severity)
                        .thenComparing(ReadinessBlocker::raisedAt))
                .map(blocker -> new FacilityDashboard.ExceptionRow(blocker.id(), "ReadinessBlocker",
                        blocker.source().name(), blocker.description(),
                        "Open since " + blocker.raisedAt(), blocker.severity().name()))
                .toList();
    }

    /** The drilldown behind the unavailable-spaces indicator. */
    @Transactional(readOnly = true)
    public List<FacilityDashboard.ExceptionRow> unavailableRows(String siteCode, ActorContext actor,
            SourceChannel channel) {
        requireDrilldown(actor, channel, siteCode);
        return unavailableSpaces(authorization.filterBySite(actor, facilities.findActiveRooms(siteCode),
                FacilityRoom::siteCode));
    }

    /** The drilldown behind the stale-readiness indicator. */
    @Transactional(readOnly = true)
    public List<FacilityDashboard.ExceptionRow> staleRows(String siteCode, ActorContext actor,
            SourceChannel channel) {
        requireDrilldown(actor, channel, siteCode);
        Instant now = clock.instant();
        Duration window = operatingModeOf(siteCode) == OperatingMode.EXAMINATION
                ? configuration.duration("facilities.readiness.examination-staleness-threshold", siteCode,
                        DEFAULT_EXAM_STALENESS)
                : configuration.duration("facilities.readiness.staleness-threshold", siteCode,
                        DEFAULT_READINESS_STALENESS);
        return staleRows(authorization.filterBySite(actor,
                facilities.findStaleReadiness(siteCode, now.minus(window)), FacilityRoom::siteCode), now);
    }

    // ---- computation --------------------------------------------------------------------------

    private FacilityDashboard.SpaceReadiness spaceReadiness(List<FacilityRoom> rooms) {
        return new FacilityDashboard.SpaceReadiness(
                rooms.size(),
                count(rooms, LocationReadinessStatus.READY),
                count(rooms, LocationReadinessStatus.DEGRADED),
                count(rooms, LocationReadinessStatus.BLOCKED),
                count(rooms, LocationReadinessStatus.UNKNOWN),
                (int) rooms.stream().filter(FacilityRoom::bookable).count(),
                (int) rooms.stream().filter(FacilityRoom::availableForBooking).count(),
                (int) rooms.stream().filter(FacilityRoom::examinationCapable).count(),
                (int) rooms.stream().filter(FacilityRoom::availableForExamination).count());
    }

    private FacilityDashboard.BlockerSummary blockerSummary(List<ReadinessBlocker> open, Instant now,
            Duration escalationWindow) {
        int critical = severityCount(open, BlockerSeverity.CRITICAL);
        int beyondWindow = (int) open.stream()
                .filter(blocker -> blocker.severity() == BlockerSeverity.CRITICAL)
                .filter(blocker -> blocker.ageAt(now).compareTo(escalationWindow) > 0)
                .count();
        return new FacilityDashboard.BlockerSummary(critical, severityCount(open, BlockerSeverity.MAJOR),
                severityCount(open, BlockerSeverity.MINOR), severityCount(open, BlockerSeverity.ADVISORY),
                open.size(), beyondWindow);
    }

    private FacilityDashboard.AssetSummary assetSummary(List<FacilityAsset> assets, Instant now,
            String siteCode) {
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        LocalDate serviceHorizon = today.plusDays(configuration
                .duration("facilities.asset.service-due-warning-window", siteCode, DEFAULT_SERVICE_WARNING)
                .toDays());
        LocalDate warrantyHorizon = today.plusDays(configuration
                .duration("facilities.asset.warranty-warning-window", siteCode, DEFAULT_WARRANTY_WARNING)
                .toDays());

        return new FacilityDashboard.AssetSummary(
                assets.size(),
                (int) assets.stream().filter(FacilityAsset::impairsReadiness).count(),
                (int) assets.stream()
                        .filter(FacilityAsset::impairsReadiness)
                        .filter(asset -> asset.criticality() == AssetCriticality.CRITICAL)
                        .count(),
                (int) assets.stream().filter(asset -> asset.serviceOverdueOn(today)).count(),
                (int) assets.stream()
                        .filter(asset -> asset.serviceDueOn() != null)
                        .filter(asset -> !asset.serviceOverdueOn(today))
                        .filter(asset -> !asset.serviceDueOn().isAfter(serviceHorizon))
                        .count(),
                (int) assets.stream()
                        .filter(asset -> asset.warrantyExpiresOn() != null)
                        .filter(asset -> !asset.warrantyExpiresOn().isAfter(warrantyHorizon))
                        .count());
    }

    /**
     * The site's overall readiness, as the share of active spaces that are READY.
     *
     * <p>A count rather than an average of assessment scores: an estate where every hall scores 90%
     * and none is usable should not report 90.
     */
    private int readinessScore(List<FacilityRoom> rooms) {
        if (rooms.isEmpty()) {
            return 0;
        }
        return Math.round((count(rooms, LocationReadinessStatus.READY) * 100f) / rooms.size());
    }

    /**
     * Examination readiness risk — the indicator SRS-SFL-S152-05 names by that phrase.
     *
     * <p>A space qualifies when it is examination-capable and something stands between it and being
     * used: a non-READY status, an open critical blocker, or open maintenance against its location.
     */
    private List<FacilityDashboard.ExceptionRow> examinationRisks(List<FacilityRoom> rooms,
            List<ReadinessBlocker> openBlockers, Set<String> maintenanceLocations) {
        List<FacilityDashboard.ExceptionRow> rows = new ArrayList<>();
        for (FacilityRoom room : rooms) {
            if (!room.examinationCapable()) {
                continue;
            }
            boolean hasCritical = openBlockers.stream()
                    .anyMatch(blocker -> blocker.roomId().equals(room.id()) && blocker.blocksReadiness());
            boolean underMaintenance = maintenanceLocations.contains(room.roomCode());
            boolean notReady = room.readinessStatus() != LocationReadinessStatus.READY;
            if (!hasCritical && !underMaintenance && !notReady) {
                continue;
            }
            String reason = hasCritical ? "Open critical readiness blocker"
                    : underMaintenance ? "Open maintenance against this location"
                    : "Readiness is " + room.readinessStatus();
            rows.add(new FacilityDashboard.ExceptionRow(room.id(), "FacilityRoom", room.roomCode(), room.name(),
                    reason, hasCritical ? "CRITICAL" : underMaintenance ? "MAJOR" : "MINOR"));
        }
        return List.copyOf(rows);
    }

    private List<FacilityDashboard.ExceptionRow> unavailableSpaces(List<FacilityRoom> rooms) {
        return rooms.stream()
                .filter(room -> room.bookable() && !room.availableForBooking())
                .map(room -> new FacilityDashboard.ExceptionRow(room.id(), "FacilityRoom", room.roomCode(),
                        room.name(), "Readiness is " + room.readinessStatus(),
                        room.readinessStatus() == LocationReadinessStatus.BLOCKED ? "CRITICAL" : "MAJOR"))
                .toList();
    }

    private List<FacilityDashboard.ExceptionRow> staleRows(List<FacilityRoom> rooms, Instant now) {
        return rooms.stream()
                .map(room -> new FacilityDashboard.ExceptionRow(room.id(), "FacilityRoom", room.roomCode(),
                        room.name(),
                        room.readinessUpdatedAt() == null
                                ? "Never assessed"
                                : "Last assessed " + room.readinessUpdatedAt(),
                        room.readinessUpdatedAt() == null ? "MAJOR" : "MINOR"))
                .toList();
    }

    private String staleWarning(int staleCount, Duration window) {
        return staleCount + " space(s) have readiness older than " + window
                + " and may not reflect the current state of the estate.";
    }

    private void requireDrilldown(ActorContext actor, SourceChannel channel, String siteCode) {
        authorization.require(actor, SflPermission.FACILITIES_DASHBOARD_DRILLDOWN, channel, "FacilityDashboard",
                "drilldown", siteCode);
        authorization.requireRequestedSite(actor, siteCode, channel, "FacilityDashboard");
    }

    /**
     * The operating mode a dashboard is read under.
     *
     * <p>Across all sites, {@code EXAMINATION} wins if any site is in it: the stricter standard is the
     * safe one to report against, and an all-sites view that showed routine thresholds while a centre
     * sat mid-examination would understate the risk.
     */
    private OperatingMode operatingModeOf(String siteCode) {
        if (siteCode != null && !siteCode.isBlank()) {
            return facilities.findSiteByCode(siteCode).map(Site::operatingMode).orElse(OperatingMode.ROUTINE);
        }
        return facilities.findSites().stream().anyMatch(Site::inExaminationMode)
                ? OperatingMode.EXAMINATION
                : OperatingMode.ROUTINE;
    }

    private static int count(List<FacilityRoom> rooms, LocationReadinessStatus status) {
        return (int) rooms.stream().filter(room -> room.readinessStatus() == status).count();
    }

    private static int severityCount(List<ReadinessBlocker> blockers, BlockerSeverity severity) {
        return (int) blockers.stream().filter(blocker -> blocker.severity() == severity).count();
    }
}
