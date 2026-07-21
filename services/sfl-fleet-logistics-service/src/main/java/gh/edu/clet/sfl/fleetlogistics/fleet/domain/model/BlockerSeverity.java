package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

/**
 * How hard a readiness or eligibility blocker bites.
 *
 * <p>The distinction is what makes "conditionally ready" meaningful: a warning tells the dispatcher
 * something needs attention soon, a blocking finding stops the assignment outright.
 */
public enum BlockerSeverity {
    WARNING,
    BLOCKING
}
