package gh.edu.clet.sfl.facilities.dashboard.domain;

import gh.edu.clet.sfl.facilities.shared.domain.model.OperatingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The S152-05 dashboard read model.
 *
 * <p>Every indicator the requirement names — "facility readiness, open service requests, unavailable
 * rooms, site compliance exceptions and examination readiness risk" — plus the two things it says
 * about how they must behave:
 *
 * <ul>
 *   <li><strong>Counts reconcile to source records.</strong> Each exception list carries the
 *       identifiers behind it, so a number can always be opened rather than only believed.</li>
 *   <li><strong>Stale data is declared.</strong> {@code stale} and {@code staleWarning} are set when
 *       the oldest input is older than the configured freshness threshold, rather than the screen
 *       quietly presenting old numbers as current.</li>
 * </ul>
 */
public record FacilityDashboard(
        String siteCode,
        OperatingMode operatingMode,
        Instant generatedAt,
        SpaceReadiness spaces,
        BlockerSummary blockers,
        AssetSummary assets,
        MaintenanceSummary maintenance,
        int readinessScore,
        boolean stale,
        String staleWarning,
        List<ExceptionRow> examinationRisks,
        List<ExceptionRow> unavailableSpaces,
        List<ExceptionRow> staleReadiness) {

    /** Readiness counts across the site's active spaces. */
    public record SpaceReadiness(
            int total,
            int ready,
            int degraded,
            int blocked,
            int unknown,
            int bookable,
            int availableForBooking,
            int examinationCapable,
            int availableForExamination) {
    }

    /** Open blockers by severity — the SRS's "blockers by severity" indicator. */
    public record BlockerSummary(
            int critical,
            int major,
            int minor,
            int advisory,
            int total,
            int criticalBeyondEscalationWindow) {
    }

    public record AssetSummary(
            int total,
            int impaired,
            int criticalImpaired,
            int serviceOverdue,
            int serviceDueSoon,
            int warrantyExpiringSoon) {
    }

    /** S153's contribution: the maintenance work that keeps a space from being ready. */
    public record MaintenanceSummary(
            int openFaults,
            int openWorkOrders) {
    }

    /**
     * One row behind a count.
     *
     * <p>SRS-SFL-S152-05: "Dashboard records shall link back to source workflows and evidence where the
     * user has permission." The id is what the drilldown opens; the reason is what makes the row
     * legible without opening it.
     */
    public record ExceptionRow(
            UUID id,
            String resourceType,
            String code,
            String label,
            String reason,
            String severity) {
    }
}
