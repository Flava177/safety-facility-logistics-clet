package gh.edu.clet.sfl.facilities.readiness.domain;

import gh.edu.clet.sfl.facilities.masterdata.domain.LocationReadinessStatus;
import java.util.List;

/**
 * The computed readiness of a space and the reasons behind it.
 *
 * <p>Status and reasons travel together on purpose. The dashboard's blocker list, the "why is this
 * hall not ready" drilldown and the refusal message when READY is denied are all the same question,
 * and returning a bare enum would force each caller to go and ask it again.
 */
public record ReadinessOutcome(
        LocationReadinessStatus status,
        int score,
        List<ReadinessBlocker> openBlockers,
        int criticalCount,
        int majorCount,
        int minorCount,
        int advisoryCount) {

    public ReadinessOutcome {
        openBlockers = openBlockers == null ? List.of() : List.copyOf(openBlockers);
    }

    /** A one-line explanation suitable for the space's readiness note. */
    public String summary() {
        if (openBlockers.isEmpty()) {
            return status == LocationReadinessStatus.READY
                    ? "All readiness checks passed."
                    : "No open blockers recorded.";
        }
        return criticalCount + " critical, " + majorCount + " major, " + minorCount + " minor and "
                + advisoryCount + " advisory blocker(s) open.";
    }

    public boolean isReady() {
        return status == LocationReadinessStatus.READY;
    }
}
